package cl.duoc.pedidos360.orders.dto;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

/** Usuario que origina la petición, propagado por el BFF en los headers X-User-*. */
public record RequestUser(String id, String name, List<String> roles) {

    public boolean isStaff() {
        return roles.contains("Admin") || roles.contains("Operador");
    }

    public static RequestUser fromHeaders(String id, String name, String roles) {
        List<String> roleList = roles == null || roles.isBlank()
                ? List.of()
                : Arrays.stream(roles.split(",")).map(String::trim).toList();
        String decodedName = name == null ? null : URLDecoder.decode(name, StandardCharsets.UTF_8);
        return new RequestUser(id == null || id.isBlank() ? "anonimo" : id, decodedName, roleList);
    }
}
