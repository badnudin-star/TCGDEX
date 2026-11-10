#!/usr/bin/env python3
"""Tests for the JVM unit-test failure reporter used by the build CI step."""
from __future__ import annotations

import io
import os
import sys
import tempfile
import unittest
from contextlib import redirect_stderr, redirect_stdout
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

from scripts.report_unit_test_failures import (  # noqa: E402
    collect_failures,
    escape,
    find_results,
    first_stack_lines,
    main,
)

UNIT_XML = """<?xml version="1.0" encoding="UTF-8"?>
<testsuite name="testDebugUnitTest" tests="4" failures="1" errors="1" skipped="0">
  <testcase name="longCjkRunsBecomeSlidingTrigrams" classname="com.pokemontcg.sammlung.CardNumberMatcherTest" time="0.02">
    <failure message="expected:&lt;[a]&gt; but was:&lt;[b]&gt;" type="org.opentest4j.AssertionFailedError">
org.opentest4j.AssertionFailedError: expected:&lt;[a]&gt; but was:&lt;[b]&gt;
	at org.junit.Assert.fail(Assert.java:89)
	at com.pokemontcg.sammlung.CardNumberMatcherTest.longCjkRunsBecomeSlidingTrigrams(CardNumberMatcherTest.kt:137)
</failure>
  </testcase>
  <testcase name="throwsOnBadInput" classname="com.pokemontcg.sammlung.ScannerOcrChainTest" time="0.01">
    <error message="NullPointerException" type="java.lang.NullPointerException">java.lang.NullPointerException</error>
  </testcase>
  <testcase name="passes" classname="com.pokemontcg.sammlung.CardNumberMatcherTest" time="0.0"/>
</testsuite>
"""


class ReporterTest(unittest.TestCase):
    def setUp(self) -> None:
        self._tmp = tempfile.TemporaryDirectory()
        self.root = Path(self._tmp.name)
        self.addCleanup(self._tmp.cleanup)

    def write_results(self, relative: str, content: str = UNIT_XML) -> Path:
        path = self.root / relative
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(content, encoding="utf-8")
        return path

    def test_escapes_percent_and_line_breaks_for_annotations(self) -> None:
        self.assertEqual(escape("50% done\r\nagain"), "50%25 done%0Aagain")

    def test_finds_results_in_both_output_roots(self) -> None:
        self.write_results("app/build/test-results/testDebugUnitTest/TEST-1.xml")
        self.write_results("app/build/reports/tests/testDebugUnitTest/classes/TEST-2.xml")
        self.write_results("app/build/intermediates/junk.xml", content="binary")

        found = find_results(str(self.root))

        self.assertEqual(2, len(found))
        self.assertTrue(all(path.endswith(".xml") for path in found))

    def test_collect_failures_reports_failure_and_error_with_labels(self) -> None:
        path = self.write_results("app/build/test-results/testDebugUnitTest/TEST-x.xml")
        failures = collect_failures([str(path)])

        labels = [label for label, _message, _stack in failures]
        self.assertEqual(
            [
                "com.pokemontcg.sammlung.CardNumberMatcherTest.longCjkRunsBecomeSlidingTrigrams",
                "com.pokemontcg.sammlung.ScannerOcrChainTest.throwsOnBadInput",
            ],
            labels,
        )
        self.assertEqual(failures[0][1], "expected:<[a]> but was:<[b]>")
        self.assertIn("CardNumberMatcherTest.kt:137", failures[0][2])

    def test_collect_failures_skips_unparsable_files(self) -> None:
        broken = self.write_results("app/build/test-results/testDebugUnitTest/TEST-broken.xml", content="<not-xml")
        self.assertEqual([], collect_failures([str(broken)]))

    def test_first_stack_lines_prefers_app_frames(self) -> None:
        stack = "at org.junit.Assert.fail(Assert.java:89)\n\tat com.pokemontcg.sammlung.X.y(X.kt:12)"
        self.assertEqual("at com.pokemontcg.sammlung.X.y(X.kt:12)", first_stack_lines(stack))

    def test_main_emits_error_annotations_and_stderr_count(self) -> None:
        self.write_results("app/build/test-results/testDebugUnitTest/TEST-x.xml")
        summary = self.root / "step-summary.md"
        os.environ["GITHUB_STEP_SUMMARY"] = str(summary)
        try:
            out, err = io.StringIO(), io.StringIO()
            with redirect_stdout(out), redirect_stderr(err):
                exit_code = main(["report_unit_test_failures.py", str(self.root)])
        finally:
            del os.environ["GITHUB_STEP_SUMMARY"]

        self.assertEqual(0, exit_code)
        self.assertIn(
            "::error::Unit-Test fehlgeschlagen: com.pokemontcg.sammlung.CardNumberMatcherTest"
            ".longCjkRunsBecomeSlidingTrigrams -> expected:<[a]> but was:<[b]>",
            out.getvalue(),
        )
        self.assertIn("2 unit test failure(s) reported", err.getvalue())
        summary_text = summary.read_text(encoding="utf-8")
        self.assertIn("### Unit tests failed", summary_text)
        self.assertIn("`com.pokemontcg.sammlung.ScannerOcrChainTest.throwsOnBadInput`", summary_text)

    def test_main_without_results_reports_on_stderr_and_exits_cleanly(self) -> None:
        summary = self.root / "step-summary.md"
        os.environ["GITHUB_STEP_SUMMARY"] = str(summary)
        try:
            out, err = io.StringIO(), io.StringIO()
            with redirect_stdout(out), redirect_stderr(err):
                exit_code = main(["report_unit_test_failures.py", str(self.root)])
        finally:
            del os.environ["GITHUB_STEP_SUMMARY"]

        self.assertEqual(0, exit_code)
        self.assertIn("Keine XML-Testergebnisse gefunden", err.getvalue())
        self.assertIn("before the tests ran", summary.read_text(encoding="utf-8"))


if __name__ == "__main__":
    unittest.main()
