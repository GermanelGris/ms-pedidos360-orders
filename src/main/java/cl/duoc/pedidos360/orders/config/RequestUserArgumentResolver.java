package cl.duoc.pedidos360.orders.config;

import org.springframework.core.MethodParameter;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import cl.duoc.pedidos360.orders.dto.RequestUser;

/** Construye el {@link RequestUser} a partir de los headers que propaga el BFF. */
public class RequestUserArgumentResolver implements HandlerMethodArgumentResolver {

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return RequestUser.class.equals(parameter.getParameterType());
    }

    @Override
    public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
                                  NativeWebRequest request, WebDataBinderFactory binderFactory) {
        return RequestUser.fromHeaders(
                request.getHeader("X-User-Id"),
                request.getHeader("X-User-Name"),
                request.getHeader("X-User-Email"),
                request.getHeader("X-Client-Ip"),
                request.getHeader("X-User-Roles"));
    }
}
