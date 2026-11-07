#!/usr/bin/env python3
"""Tests for the connected-test failure reporter used by the emulator CI step."""
from __future__ import annotations

import io
import os
import sys
import tempfile
import unittest
from contextlib import redirect_stderr, redirect_stdout
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

from scripts.report_instrumented_failures import (  # noqa: E402
    collect_failures,
    escape,
    find_results,
    first_stack_lines,
    main,
)

CONNECTED_XML = """<?xml version="1.0" encoding="UTF-8"?>
<testsuite name="Pixel_7_API_34(AVD) - 14" tests="6" failures="1" errors="0" skipped="0">
  <testcase name="rarityMultiSelect" classname="com.pokemontcg.sammlung.CatalogSearchDaoTest" time="0.4">
    <failure message="expected:&lt;[a]&gt; but was:&lt;[b]&gt;" type="java.lang.AssertionError">
java.lang.AssertionError: expected:&lt;[a]&gt; but was:&lt;[b]&gt;
	at org.junit.Assert.fail(Assert.java:89)
	at com.pokemontcg.sammlung.CatalogSearchDaoTest.rarityMultiSelect(CatalogSearchDaoTest.kt:88)
</failure>
  </testcase>
  <testcase name="passes" classname="com.pokemontcg.sammlung.CatalogSearchDaoTest" time="0.1"/>
</testsuite>
"""


class ReporterTest(unittest.TestCase):
    def setUp(self) -> None:
        self._tmp = tempfile.TemporaryDirectory()
        self.root = Path(self._tmp.name)
        self.addCleanup(self._tmp.cleanup)

    def write_results(self, relative: str, content: str = CONNECTED_XML) -> Path:
        path = self.root / relative
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(content, encoding="utf-8")
        return path

    def test_escapes_percent_and_line_breaks_for_annotations(self) -> None:
        self.assertEqual(escape("50% done\r\nagain"), "50%25 done%0Aagain")

    def test_finds_results_in_both_output_roots(self) -> None:
        self.write_results("app/build/outputs/androidTest-results/connected/debug/TEST-1.xml")
        self.write_results("app/build/reports/androidTests/connected/TEST-2.xml")
        self.write_results("app/build/outputs/apk/debug/app-debug.apk", content="binary")

        found = find_results(str(self.root))

        self.assertEqual(2, len(found))
        self.assertTrue(all(path.endswith(".xml") for path in found))

    def test_collects_only_failing_cases_with_label_message_and_stack(self) -> None:
        self.write_results("app/build/outputs/androidTest-results/connected/debug/TEST-1.xml")

        failures = collect_failures(find_results(str(self.root)))

        self.assertEqual(1, len(failures))
        label, message, stack = failures[0]
        self.assertEqual("com.pokemontcg.sammlung.CatalogSearchDaoTest.rarityMultiSelect", label)
        self.assertIn("but was", message)
        self.assertIn("CatalogSearchDaoTest.kt:88", stack)

    def test_ignores_unparsable_result_files(self) -> None:
        self.write_results("app/build/outputs/androidTest-results/connected/debug/TEST-broken.xml", "<testsuite")

        self.assertEqual([], collect_failures(find_results(str(self.root))))

    def test_prefers_app_frames_and_caps_the_stack_excerpt(self) -> None:
        stack = "\n".join(
            ["at org.junit.Assert.fail(Assert.java:89)"]
            + [f"at com.pokemontcg.sammlung.Frame{i}.run(Frame{i}.kt:{i})" for i in range(1, 8)]
        )

        excerpt = first_stack_lines(stack)

        self.assertEqual(4, len(excerpt.split(" / ")))
        self.assertNotIn("org.junit", excerpt)

    def test_main_reports_every_failure_as_annotation_and_summary(self) -> None:
        self.write_results("app/build/outputs/androidTest-results/connected/debug/TEST-1.xml")
        summary = self.root / "summary.md"
        stdout, stderr = io.StringIO(), io.StringIO()
        previous = os.environ.get("GITHUB_STEP_SUMMARY")
        os.environ["GITHUB_STEP_SUMMARY"] = str(summary)
        try:
            with redirect_stdout(stdout), redirect_stderr(stderr):
                code = main(["prog", str(self.root)])
        finally:
            if previous is None:
                os.environ.pop("GITHUB_STEP_SUMMARY", None)
            else:
                os.environ["GITHUB_STEP_SUMMARY"] = previous

        self.assertEqual(0, code)
        annotation = stdout.getvalue()
        self.assertIn("::error::Instrumented-Test fehlgeschlagen:", annotation)
        self.assertIn("CatalogSearchDaoTest.rarityMultiSelect", annotation)
        self.assertNotIn("\n\n", annotation.strip())  # one line per annotation
        self.assertIn("but was", summary.read_text(encoding="utf-8"))

    def test_main_says_so_when_the_run_died_before_producing_results(self) -> None:
        stdout, stderr = io.StringIO(), io.StringIO()

        with redirect_stdout(stdout), redirect_stderr(stderr):
            code = main(["prog", str(self.root)])

        self.assertEqual(0, code)
        self.assertEqual("", stdout.getvalue())
        self.assertIn("Keine XML-Testergebnisse", stderr.getvalue())


if __name__ == "__main__":
    unittest.main(verbosity=2)
