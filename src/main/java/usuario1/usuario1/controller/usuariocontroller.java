package usuario1.usuario1.controller;

import java.util.ArrayList;
import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController

@RequestMapping("/usuarios")

public class usuariocontroller {

  private final List<usuario> usuarios = new ArrayList<>();

  @GetMapping

  public List<usuario> listarUsuarios() {

    return usuarios;

  }



  @PostMapping

  public usuario agregarUsuario(@RequestBody usuario usuario) {

    usuarios.add(usuario);

    return usuario;

  }

}
