package internal.org.springframework.content.rest.controllers;

import internal.org.springframework.content.rest.io.AssociatedResource;
import internal.org.springframework.content.rest.io.AssociatedResourceImpl;
import internal.org.springframework.content.rest.io.RenderableResourceImpl;
import internal.org.springframework.content.rest.utils.StoreUtils;
import org.springframework.content.commons.renditions.Renderable;
import org.springframework.content.commons.repository.AssociativeStore;
import org.springframework.content.commons.repository.Store;
import org.springframework.content.commons.storeservice.StoreInfo;
import org.springframework.content.commons.storeservice.Stores;
import org.springframework.content.rest.config.RestConfiguration;
import org.springframework.core.MethodParameter;
import org.springframework.core.io.Resource;
import org.springframework.data.repository.support.Repositories;
import org.springframework.data.repository.support.RepositoryInvokerFactory;
import org.springframework.http.HttpMethod;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.ModelAndViewContainer;
import org.springframework.web.util.UrlPathHelper;

import javax.servlet.http.HttpServletRequest;

public class ResourceHandlerMethodArgumentResolver extends StoreHandlerMethodArgumentResolver {

    public ResourceHandlerMethodArgumentResolver(RestConfiguration config, Repositories repositories, RepositoryInvokerFactory repoInvokerFactory, Stores stores) {
        super(config, repositories, repoInvokerFactory, stores);
    }

    @Override
    public boolean supportsParameter(MethodParameter methodParameter) {
        return Resource.class.isAssignableFrom(methodParameter.getParameterType());
    }

    @Override
    public Object resolveArgument(MethodParameter methodParameter, ModelAndViewContainer modelAndViewContainer, NativeWebRequest nativeWebRequest, WebDataBinderFactory webDataBinderFactory) throws Exception {

        Resource r = null;

        String pathInfo = nativeWebRequest.getNativeRequest(HttpServletRequest.class).getRequestURI();
        pathInfo = new UrlPathHelper().getPathWithinApplication(nativeWebRequest.getNativeRequest(HttpServletRequest.class));
        pathInfo = StoreUtils.storeLookupPath(pathInfo, this.getConfig().getBaseUri());

        String[] pathSegments = pathInfo.split("/");
        if (pathSegments.length < 2) {
            return null;
        }

        String store = pathSegments[1];

        StoreInfo info = this.getStores().getStore(Store.class, StoreUtils.withStorePath(store));
        if (info == null) {
            throw new IllegalArgumentException(String.format("Store for path %s not found", store));
        }

        if (AssociativeStore.class.isAssignableFrom(info.getInterface())) {

            // entity content
            if (pathSegments.length == 3) {
                String id = pathSegments[2];

                Object domainObj = findOne(this.getRepoInvokerFactory(), this.getRepositories(), info.getDomainObjectClass(), id);

                r = info.getImplementation(AssociativeStore.class).getResource(domainObj);
                r = new AssociatedResourceImpl(domainObj, r);

                if (Renderable.class.isAssignableFrom(info.getInterface())) {
                    r = new RenderableResourceImpl((Renderable)info.getImplementation(AssociativeStore.class), (AssociatedResource)r);
                }

            }
            // property content
            else {
                HttpMethod method = HttpMethod.valueOf(nativeWebRequest.getNativeRequest(HttpServletRequest.class).getMethod());
                r = (Resource) this.resolveProperty(method, this.getRepositories(), info, pathSegments, (i, e, p, propertyIsEmbedded) -> {

                    AssociativeStore s = i.getImplementation(AssociativeStore.class);
                    Resource resource = s.getResource(p);
                    resource = new AssociatedResourceImpl(p, resource);
                    if (Renderable.class.isAssignableFrom(i.getInterface())) {
                        resource = new RenderableResourceImpl((Renderable)i.getImplementation(AssociativeStore.class), (AssociatedResource)resource);
                    }
                    return resource;
                });
            }
        // do store resource resolution
        } else if (Store.class.isAssignableFrom(info.getInterface())) {
            String path = new UrlPathHelper().getPathWithinApplication(nativeWebRequest.getNativeRequest(HttpServletRequest.class));
            String pathToUse = path.substring(StoreUtils.storePath(info).length() + 1);

            r = info.getImplementation(Store.class).getResource(pathToUse);
        }

        return r;
    }
}
