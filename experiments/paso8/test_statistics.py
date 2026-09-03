import unittest

from run_paso8 import exact_mann_whitney_p, mann_whitney_u


class ExactMannWhitneyTest(unittest.TestCase):
    def test_minimum_two_sided_p_for_five_by_five(self):
        result = mann_whitney_u([1, 2, 3, 4, 5], [6, 7, 8, 9, 10])
        self.assertAlmostEqual(2 / 252, result["p_exacto"], places=9)

    def test_exact_probability_is_not_reported_with_ties(self):
        self.assertIsNone(exact_mann_whitney_p([1, 1], [2, 3], 0))


if __name__ == "__main__":
    unittest.main()
