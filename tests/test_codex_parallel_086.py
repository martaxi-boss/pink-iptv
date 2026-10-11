"""Project Leader: verify two Codex agents, no overlapping mutable scopes."""
from pathlib import Path
import tomllib
import unittest


ROOT = Path(__file__).resolve().parents[1]


class CodexParallelConfigurationTests(unittest.TestCase):
    def test_two_worker_limit_and_low_cost_default(self):
        config = tomllib.loads((ROOT / ".codex/config.toml").read_text())
        agents = config["agents"]
        self.assertIs(agents["enabled"], True)
        self.assertEqual(agents["max_concurrent_threads_per_session"], 2)
        self.assertEqual(agents["default_subagent_reasoning_effort"], "low")
        self.assertNotIn("model", config)  # user account selects an available model

    def test_distinct_scope_and_non_deployment(self):
        expected = {
            "pink_android": ("clients/extreme/", "android/", "design/"),
            "pink_backend": ("backend/",),
        }
        files = list((ROOT / ".codex/agents").glob("*.toml"))
        self.assertEqual(len(files), 2)
        for file in files:
            agent = tomllib.loads(file.read_text())
            self.assertEqual(agent["name"], file.stem)
            self.assertIn(agent["name"], expected)
            self.assertEqual(agent["sandbox_mode"], "workspace-write")
            instructions = agent["developer_instructions"]
            self.assertIn("Never", instructions)
            self.assertTrue(all(prefix in instructions for prefix in expected[agent["name"]]))
            self.assertIn(".github/", instructions)
            self.assertIn("ops/", instructions)
            self.assertNotIn("danger-full-access", file.read_text())
            self.assertNotIn("bypass", file.read_text())
            self.assertNotIn("OPENAI_API_KEY", file.read_text())

    def test_sequencing_budget_and_production_safety(self):
        instructions = (ROOT / "AGENTS.md").read_text()
        for word in ("Parallel on owner request", "Serial on owner request",
                     "LOW:", "MEDIUM:", "HIGH:", "UNKNOWN",
                     "com.pinkiptv.extreme.qa", "10 installations",
                     "serialize", "exact implementation"):
            self.assertIn(word, instructions)
        self.assertIn("subagents never perform", instructions)
        self.assertIn("return short", instructions.lower())


if __name__ == "__main__":
    unittest.main()
