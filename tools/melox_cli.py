#!/usr/bin/env python3
"""Control MeloX's experimental CLI endpoint over ADB (Python standard library only)."""
import argparse
import base64
import json
import re
import shlex
import subprocess
import sys


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--serial", "-s", help="ADB device serial")
    parser.add_argument("--package", default="com.lladlam.melox.android", help="Use com.lladlam.melox.android.dev for debug builds")
    parser.add_argument("--adb", default="adb", help="ADB executable")
    commands = parser.add_subparsers(dest="command", required=True)
    for command in ("status", "queue", "play", "pause", "toggle", "next", "previous", "stop"):
        commands.add_parser(command)
    for command, help_text in (("seek", "Position in milliseconds"), ("volume", "Player volume, 0–1")):
        commands.add_parser(command).add_argument("value", help=help_text)
    commands.add_parser("shuffle").add_argument("value", choices=("on", "off"))
    commands.add_parser("repeat").add_argument("value", choices=("off", "one", "all"))
    for command in ("search", "search-play"):
        sub = commands.add_parser(command)
        sub.add_argument("query", help="Song name / artist")
        sub.add_argument("--source", default="netease", help="Provider storage ID, e.g. netease, qq_music, qishui")
        if command == "search-play":
            sub.add_argument("--index", default=0, type=int, help="Zero-based result index (default 0)")
    args = parser.parse_args()
    adb = [args.adb] + (["-s", args.serial] if args.serial else [])
    broadcast = ["am", "broadcast", "--include-stopped-packages",
                 "-n", args.package + "/com.lladlam.melox.platform.cli.MeloXCliReceiver",
                 "--es", "command", args.command]
    for key in ("query", "source", "value"):
        if hasattr(args, key):
            broadcast += ["--es", key, str(getattr(args, key))]
    if hasattr(args, "index"):
        broadcast += ["--es", "value", str(args.index)]
    try:
        result = subprocess.run(adb + ["shell", shlex.join(broadcast)], capture_output=True, text=True, timeout=60)
        match = re.search(r"melox:([A-Za-z0-9+/=]+)", result.stdout)
        if not match:
            raise RuntimeError(result.stderr.strip() or result.stdout.strip() or "ADB 没有返回 CLI 结果")
        reply = json.loads(base64.b64decode(match.group(1), validate=True).decode("utf-8"))
        print(json.dumps(reply, ensure_ascii=False, indent=2))
        return 0 if reply.get("ok") else 1
    except (OSError, ValueError, RuntimeError, subprocess.TimeoutExpired) as error:
        print(json.dumps({"ok": False, "error": str(error)}, ensure_ascii=False), file=sys.stderr)
        return 1


if __name__ == "__main__":
    sys.exit(main())
