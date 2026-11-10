#!/usr/bin/env python3
"""Turns JVM unit-test failures into GitHub annotations.

`testDebugUnitTest` otherwise only names its failures in the job log, which is not
readable from every environment (artifact and log downloads are blocked outside
github.com). The XML results are parsed here so a failing unit test names itself
in the check annotations and in the step summary - the same pattern the
instrumented-test reporter already uses.

If the build died before the tests produced results (compilation, dependency
resolution), no XML exists; the caller falls back to the Gradle log lines and
this script says so on stderr.
"""
from __future__ import annotations

import glob
import os
import sys
import xml.etree.ElementTree as ET

RESULT_GLOBS = (
    "app/build/test-results/testDebugUnitTest/**/*.xml",
    "app/build/reports/tests/testDebugUnitTest/**/*.xml",
)

MAX_ANNOTATION_CHARS = 600
MAX_SUMMARY_STACK_CHARS = 1500


def find_results(root: str) -> list[str]:
    found: list[str] = []
    for pattern in RESULT_GLOBS:
        found.extend(glob.glob(os.path.join(root, pattern), recursive=True))
    return sorted(set(found))


def escape(message: str) -> str:
    """`%` first, otherwise GitHub reads the rest as a property block."""
    return message.replace("%", "%25").replace("\r", "").replace("\n", "%0A")


def collect_failures(paths: list[str]) -> list[tuple[str, str, str]]:
    failures: list[tuple[str, str, str]] = []
    for path in paths:
        try:
            tree = ET.parse(path)
        except (ET.ParseError, OSError):
            continue
        for case in tree.iter("testcase"):
            problems = list(case.iter("failure")) + list(case.iter("error"))
            for problem in problems:
                classname = case.get("classname") or ""
                name = case.get("name") or ""
                label = f"{classname}.{name}" if classname else name
                message = (problem.get("message") or "").strip()
                stack = (problem.text or "").strip()
                failures.append((label, message, stack))
    return failures


def first_stack_lines(stack: str, limit: int = 4) -> str:
    lines = [line.strip() for line in stack.splitlines() if line.strip()]
    app_lines = [line for line in lines if "com.pokemontcg.sammlung" in line]
    return " / ".join((app_lines or lines)[:limit])


def write_summary(failures: list[tuple[str, str, str]]) -> None:
    summary = os.environ.get("GITHUB_STEP_SUMMARY")
    if not summary:
        return
    with open(summary, "a", encoding="utf-8") as handle:
        handle.write("### Unit tests failed\n\n")
        for label, message, stack in failures:
            handle.write(f"- `{label}`: {message or 'siehe Stack'}\n\n")
            handle.write(f"```\n{stack[:MAX_SUMMARY_STACK_CHARS]}\n```\n")


def main(argv: list[str]) -> int:
    root = argv[1] if len(argv) > 1 else "."
    failures = collect_failures(find_results(root))
    if not failures:
        print(
            "Keine XML-Testergebnisse gefunden: Der Lauf ist vor den Tests abgebrochen "
            "(Kompilierung oder Abhängigkeitsauflösung).",
            file=sys.stderr,
        )
        summary = os.environ.get("GITHUB_STEP_SUMMARY")
        if summary:
            with open(summary, "a", encoding="utf-8") as handle:
                handle.write(
                    "### Unit tests\n\nNo XML results were produced, so the build stopped "
                    "before the tests ran (compilation or dependency resolution).\n"
                )
        return 0
    for label, message, stack in failures:
        detail = message or first_stack_lines(stack)
        print(f"::error::Unit-Test fehlgeschlagen: {label} -> {escape(detail[:MAX_ANNOTATION_CHARS])}")
    write_summary(failures)
    print(f"{len(failures)} unit test failure(s) reported", file=sys.stderr)
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv))
