package no.entur.kakka.config;

import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.EncodedResource;
import org.springframework.core.io.support.PropertySourceFactory;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.util.List;

public class YamlPropertySourceFactory implements PropertySourceFactory {
    @Override
    public PropertySource<?> createPropertySource(String name, EncodedResource resource) throws IOException {
        List<PropertySource<?>> propertySource = null;
        try {
            if (name != null) {
                propertySource = new YamlPropertySourceLoader().load(name, resource.getResource());
            } else {
                propertySource = new YamlPropertySourceLoader().load(getNameForResource(resource.getResource()), resource.getResource());
            }
        } catch (Exception fileNotFoundException) {
            //Ignore - look up in filesystem below
        }

        // Properties not found through classpath - resolve properties from absolute path
        if (propertySource == null) {
            String path = ((ClassPathResource) resource.getResource()).getPath();
            if (!path.startsWith("/")) {
                path = "/"+path;
            }
            propertySource = new YamlPropertySourceLoader().load(null, new FileSystemResource(path));
        }

        if (propertySource != null && !propertySource.isEmpty()) {
            return propertySource.get(0);
        }
        return null;
    }
    private static String getNameForResource(Resource resource) {
        String name = resource.getDescription();
        if (!StringUtils.hasText(name)) {
            name = resource.getClass().getSimpleName() + "@" + System.identityHashCode(resource);
        }
        return name;
    }
}
