package ru.finex.evolution.impl;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Multimaps;
import lombok.RequiredArgsConstructor;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import ru.finex.evolution.ClasspathScanner;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * @author m0nster.mind
 */
@Singleton
@RequiredArgsConstructor(onConstructor_ = { @Inject })
public class MigrationParser {

    private static final Pattern FILE_PATTERN = Pattern.compile("([\\w\\d]+)_(\\d+)(?>_([\\w\\d_\\-]+))?\\.sql");
    private static final Pattern UP_PATTERN = Pattern.compile("#\\s*---\\s*!Ups\\s*");
    private static final Pattern DOWN_PATTERN = Pattern.compile("#\\s*---\\s*!Downs\\s*");
    private static final Pattern PROCEDURE = Pattern.compile(";;");
    private static final Pattern END_QUERY = Pattern.compile(";");

    private final ClasspathScanner scanner;

    @SuppressWarnings("checkstyle:MissingJavadocMethod")
    public ListMultimap<String, MigrationData> parseAll() {
        return scanner.getResources(FILE_PATTERN)
            .stream()
            .map(this::createMigration)
            .collect(Multimaps.toMultimap(
                MigrationData::getComponent,
                Function.identity(),
                ArrayListMultimap::create
            ));
    }

    private MigrationData createMigration(String resourcePath) {
        MigrationData data = new MigrationData();
        fillMigrationMetaInformation(data, resourcePath);
        parseQueries(data, resourcePath);
        return data;
    }

    private void fillMigrationMetaInformation(MigrationData data, String resourcePath) {
        Matcher matcher = FILE_PATTERN.matcher(resourcePath);
        if (!matcher.find()) {
            throw new RuntimeException("Invalid evolution name: " + resourcePath);
        }

        data.setComponent(matcher.group(1));
        data.setVersion(Integer.parseInt(matcher.group(2)));
        data.setName(matcher.group(3));
    }

    private void parseQueries(MigrationData data, String resourcePath) {
        String content;
        try {
            content = IOUtils.resourceToString(resourcePath, StandardCharsets.UTF_8, this.getClass().getClassLoader());
        } catch (IOException e) {
            throw new RuntimeException("Fail to read evolution: " + resourcePath, e);
        }

        List<String> queries = data.getUpQueries();
        StringBuilder query = new StringBuilder();
        String[] lines = content.split("(\r\n)|\r|\n");
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];

            if (UP_PATTERN.matcher(line).matches()) {
                if (query.length() != 0) {
                    queries.add(query.toString());
                    query.setLength(0);
                }
                queries = data.getUpQueries();
            } else if (DOWN_PATTERN.matcher(line).matches()) {
                if (query.length() != 0) {
                    queries.add(query.toString());
                    query.setLength(0);
                }
                queries = data.getDownQueries();
            }

            if (StringUtils.isBlank(line) || line.charAt(0) == '#') {
                continue;
            }

            boolean endQuery = false;
            Matcher procedureMatcher = PROCEDURE.matcher(line);
            Matcher endQueryMatcher = END_QUERY.matcher(line);
            if (procedureMatcher.find()) {
                line = procedureMatcher.replaceAll(";");
            } else if (endQueryMatcher.find()) {
                line = endQueryMatcher.replaceFirst("");
                endQuery = true;
            }

            query.append(line);

            if (endQuery) {
                queries.add(query.toString());
                query.setLength(0);
            }
        }

        if (query.length() != 0) {
            queries.add(query.toString());
        }
    }

}
