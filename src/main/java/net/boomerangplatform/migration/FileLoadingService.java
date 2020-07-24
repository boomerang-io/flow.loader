package net.boomerangplatform.migration;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.LinkedList;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.io.support.ResourcePatternUtils;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;

@Component
public class FileLoadingService {

    @Autowired
    private ResourceLoader resourceLoader;

    public List<String> loadFiles(String pattern) throws IOException {

        final List<String> files = new LinkedList<>();

        final Resource[] resources = ResourcePatternUtils.getResourcePatternResolver(resourceLoader)
                .getResources(pattern);
        for (final Resource resource : resources) {
            final String json = StreamUtils.copyToString(resource.getInputStream(), Charset.defaultCharset());
            files.add(json);
        }

        return files;
    }
}
