import base64
import contextlib
import io
import json
import shlex
import subprocess
import unittest
from unittest.mock import patch

import melox_cli


class MeloXCliTest(unittest.TestCase):
    def invoke(self, arguments, reply):
        encoded = base64.b64encode(json.dumps(reply, ensure_ascii=False).encode()).decode()
        output = subprocess.CompletedProcess([], 0, 'Broadcast completed: result=0, data="melox:' + encoded + '"', '')
        stream = io.StringIO()
        with patch("sys.argv", ["melox_cli.py"] + arguments), patch("subprocess.run", return_value=output) as run, contextlib.redirect_stdout(stream):
            code = melox_cli.main()
        return code, json.loads(stream.getvalue()), run.call_args.args[0]

    def test_unicode_and_shell_metacharacters_are_one_query_argument(self):
        query = "周杰伦 '晴天'; $(echo bad)"
        code, reply, argv = self.invoke(["--serial", "device", "search", query, "--source", "qishui"], {"ok": True, "tracks": [{"title": "晴天"}]})
        self.assertEqual(0, code)
        self.assertEqual("晴天", reply["tracks"][0]["title"])
        self.assertEqual(["adb", "-s", "device", "shell"], argv[:-1])
        shell_args = shlex.split(argv[-1])
        self.assertEqual(query, shell_args[shell_args.index("query") + 1])

    def test_application_error_has_nonzero_exit(self):
        code, reply, _ = self.invoke(["pause"], {"ok": False, "error": "CLI 已关闭"})
        self.assertEqual(1, code)
        self.assertFalse(reply["ok"])

    def test_search_play_index_is_sent_as_value(self):
        _, _, argv = self.invoke(["--package", "test.package", "search-play", "歌曲", "--index", "2"], {"ok": True})
        shell_args = shlex.split(argv[-1])
        self.assertIn("test.package/com.lladlam.melox.platform.cli.MeloXCliReceiver", shell_args)
        self.assertEqual("2", shell_args[shell_args.index("value") + 1])


if __name__ == "__main__":
    unittest.main()
