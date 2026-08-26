package com.tiendatech.frontend;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.servlet.view.RedirectView;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class WebappController {

    @GetMapping({"/app", "/app/"})
    public String webapp() {
        return "forward:/app/index.html";
    }

    @GetMapping({"/Login.html", "/login.html"})
    public RedirectView login() { return new RedirectView("/app/#/login"); }

    @GetMapping("/CrearUsuario.html")
    public RedirectView register() { return new RedirectView("/app/#/registro"); }

    @GetMapping("/Recuperacion.html")
    public RedirectView recovery() { return new RedirectView("/app/#/recuperacion"); }

    @GetMapping("/cuenta.html")
    public RedirectView account() { return new RedirectView("/app/#/cuenta"); }

    @GetMapping("/pago.html")
    public RedirectView checkout() { return new RedirectView("/app/#/pago"); }

    @GetMapping("/Carrito.html")
    public RedirectView cart() { return new RedirectView("/app/#/carrito"); }

    @GetMapping("/Armado.html")
    public RedirectView builder() { return new RedirectView("/app/#/armado"); }

    @GetMapping({"/", "/index.html", "/Busqueda.html"})
    public RedirectView catalog() { return new RedirectView("/app/#/"); }

    @GetMapping("/informacion_producto.html")
    public RedirectView product(@RequestParam(required = false) String id) {
        return new RedirectView(id == null ? "/app/#/" : "/app/#/producto/" + id);
    }

    @GetMapping("/ActualizarContrasenia.html")
    public RedirectView password() { return new RedirectView("/app/#/cuenta"); }

    @GetMapping("/factura.html")
    public RedirectView invoice(@RequestParam(required = false) String id,
                                @RequestParam(required = false) String facturaId) {
        String value = id != null ? id : facturaId;
        return new RedirectView(value == null ? "/app/#/factura" : "/app/#/factura/" + value);
    }

    @GetMapping({"/admin.html", "/editar_usuario.html", "/EditarTrabajadores.html"})
    public RedirectView admin() { return new RedirectView("/app/#/admin"); }

    @GetMapping("/trabajador.html")
    public RedirectView worker() { return new RedirectView("/app/#/trabajador"); }
}
