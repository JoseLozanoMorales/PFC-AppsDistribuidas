import tempfile
import unittest
from pathlib import Path

from coordination_lab import Case, FaultInjector, Lab, compatibility_cases, make_cases, oracle


class CoordinationLabTest(unittest.TestCase):
    def run_strategy(self, coord, mode, probability):
        with tempfile.TemporaryDirectory() as directory:
            lab = Lab(Path(directory) / "lab.db", coord, FaultInjector(mode, probability, 7, 0.001))
            lab.reset(10)
            result = lab.purchase(Case(1, 1, 2, 3000, mode, probability, 7))
            return result, oracle(lab.db_path)

    def test_both_strategies_confirm(self):
        for coord in ("2pc", "saga"):
            result, report = self.run_strategy(coord, "none", 0)
            self.assertTrue(result["success"])
            self.assertTrue(report["pass"])

    def test_both_strategies_remain_consistent_after_timeout(self):
        for coord in ("2pc", "saga"):
            result, report = self.run_strategy(coord, "omission", 1)
            self.assertFalse(result["success"])
            self.assertTrue(report["pass"])

    def test_case_bank_is_deterministic(self):
        self.assertEqual(make_cases(120, 99), make_cases(120, 99))
        self.assertEqual(120, len(make_cases(120, 99)))
        self.assertEqual(compatibility_cases(120, 99), compatibility_cases(120, 99))
        self.assertEqual(120, len(compatibility_cases(120, 99)))


if __name__ == "__main__":
    unittest.main()
