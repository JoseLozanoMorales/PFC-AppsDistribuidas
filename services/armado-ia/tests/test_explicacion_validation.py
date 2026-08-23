import unittest

from app.explicacion.client import ContextoExplicacion
from app.explicacion.service import ExplicacionService
from app.explicacion.validation import build_explanation_validation_chain


class ExplanationValidationChainTest(unittest.TestCase):
    def setUp(self) -> None:
        self.context = ContextoExplicacion(
            porcentaje_bottleneck=8.0,
            nivel="bajo",
            componente_limitante="GPU",
            advertencias=[],
            componentes_nombres={"gpu": "NVIDIA RTX 4060"},
        )
        self.chain = build_explanation_validation_chain()

    def test_rejects_empty_response_in_first_handler(self) -> None:
        issue = self.chain.validate("   ", self.context)

        self.assertIsNotNone(issue)
        self.assertEqual("empty_response", issue.code)

    def test_rejects_unverified_hardware_in_second_handler(self) -> None:
        issue = self.chain.validate("Conviene instalar una RTX 4090", self.context)

        self.assertIsNotNone(issue)
        self.assertEqual("unverified_hardware", issue.code)
        self.assertEqual("RTX 4090", issue.detail)

    def test_accepts_hardware_present_in_input_context(self) -> None:
        issue = self.chain.validate("La NVIDIA RTX 4060 es el componente analizado", self.context)

        self.assertIsNone(issue)


class _StaticClient:
    def __init__(self, response: str) -> None:
        self.response = response

    def explicar(self, contexto: ContextoExplicacion) -> str:
        del contexto
        return self.response


class ExplanationServiceValidationTest(unittest.TestCase):
    def setUp(self) -> None:
        self.context = ContextoExplicacion(
            porcentaje_bottleneck=8.0,
            nivel="bajo",
            componente_limitante="GPU",
            advertencias=[],
            componentes_nombres={"gpu": "NVIDIA RTX 4060"},
        )

    def test_returns_valid_llm_response(self) -> None:
        service = ExplicacionService(
            _StaticClient("La RTX 4060 tiene un cuello de botella bajo"),
            _StaticClient("fallback"),
        )

        self.assertEqual(
            "La RTX 4060 tiene un cuello de botella bajo",
            service.generar(self.context),
        )

    def test_uses_fallback_when_chain_rejects_response(self) -> None:
        service = ExplicacionService(
            _StaticClient("Compra una RTX 4090"),
            _StaticClient("fallback seguro"),
        )

        self.assertEqual("fallback seguro", service.generar(self.context))


if __name__ == "__main__":
    unittest.main()
