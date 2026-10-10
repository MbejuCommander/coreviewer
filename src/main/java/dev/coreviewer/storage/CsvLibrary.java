package dev.coreviewer.storage;

import com.google.gson.*;

import dev.coreviewer.model.*;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;

/** Called only by the serialized storage worker. The visible library never follows symlinks. */
public final class CsvLibrary {
    public record FileEntry(String name, boolean selected, int count, String warning) {}

    private record Binding(String name, boolean selected, String server) {}

    private static final Gson JSON = new GsonBuilder().setPrettyPrinting().create();
    private final Path root, folder;
    private final List<Binding> bindings = new ArrayList<>();
    private List<FileEntry> entries = List.of();
    private Set<String> entityNames = Set.of();

    private record Cached(
            long size,
            java.nio.file.attribute.FileTime modified,
            String server,
            CsvEvents.Result result) {}

    private final LinkedHashMap<String, Cached> cache = new LinkedHashMap<>(16, .75f, true);
    private int cachedEvents;
    private String simpleFile = "simple.csv";

    private CsvEvents.Result read(Binding binding) throws IOException {
        Path file = path(binding.name());
        var attributes =
                Files.readAttributes(
                        file,
                        java.nio.file.attribute.BasicFileAttributes.class,
                        LinkOption.NOFOLLOW_LINKS);
        var prior = cache.get(binding.name());
        if (prior != null
                && prior.size() == attributes.size()
                && prior.modified().equals(attributes.lastModifiedTime())
                && prior.server().equals(binding.server())) return prior.result();
        var result = CsvEvents.read(Files.readString(file), binding.server(), entityNames);
        if (prior != null) {
            cache.remove(binding.name());
            cachedEvents -= prior.result().events().size();
        }
        if (result.events().size() <= 200000) {
            cache.put(
                    binding.name(),
                    new Cached(
                            attributes.size(),
                            attributes.lastModifiedTime(),
                            binding.server(),
                            result));
            cachedEvents += result.events().size();
            while (cache.size() > 8 || cachedEvents > 200000) {
                var first = cache.pollFirstEntry();
                cachedEvents -= first.getValue().result().events().size();
            }
        }
        return result;
    }

    private void invalidate(String name) {
        var prior = cache.remove(name);
        if (prior != null) cachedEvents -= prior.result().events().size();
    }

    public void entityNames(Set<String> names) {
        entityNames = Set.copyOf(names);
        cache.clear();
        cachedEvents = 0;
    }

    public CsvLibrary(Path root) {
        this.root = root;
        folder = root.resolve("csv");
    }

    public Path folder() {
        return folder;
    }

    public List<FileEntry> entries() {
        return entries;
    }

    public void initialize() throws IOException {
        Files.createDirectories(folder);
        Path simplePointer = root.resolve("simple-csv.json");
        if (Files.exists(simplePointer)) {
            try {
                simpleFile = JSON.fromJson(Files.readString(simplePointer), String.class);
                path(simpleFile);
            } catch (RuntimeException ex) {
                throw new IOException("Invalid simple CSV pointer", ex);
            }
        }
        Path manifest = root.resolve("csv-library.json");
        if (Files.exists(manifest)) {
            try {
                bindings.addAll(
                        Arrays.asList(JSON.fromJson(Files.readString(manifest), Binding[].class)));
            } catch (RuntimeException ex) {
                throw new IOException("Invalid CSV library manifest", ex);
            }
        } else if (Files.exists(root.resolve("events.json"))) {
            var legacy = EventCodec.parseJson(Files.readString(root.resolve("events.json")));
            if (!legacy.isEmpty()) write("migrated-events.csv", legacy, "");
        }
    }

    private Path path(String name) throws IOException {
        Path base = folder.toAbsolutePath().normalize();
        Path relative = Path.of(name.replace('\\', '/'));
        Path target = base.resolve(relative).normalize();
        if (relative.isAbsolute()
                || !target.startsWith(base)
                || target.equals(base)
                || !name.toLowerCase(Locale.ROOT).endsWith(".csv"))
            throw new IOException("Invalid CSV filename");
        for (Path current = target;
                current != null && current.startsWith(base);
                current = current.getParent())
            if (Files.isSymbolicLink(current))
                throw new IOException("CSV symlinks are not supported");
        return target;
    }

    private String relative(Path file) {
        return folder.toAbsolutePath()
                .normalize()
                .relativize(file.toAbsolutePath().normalize())
                .toString()
                .replace('\\', '/');
    }

    private List<Path> files() throws IOException {
        // Files.walk does not follow directory links. Validate every ancestor before opening.
        try (var stream = Files.walk(folder)) {
            return stream.filter(
                            p ->
                                    Files.isRegularFile(p, LinkOption.NOFOLLOW_LINKS)
                                            && p.toString()
                                                    .toLowerCase(Locale.ROOT)
                                                    .endsWith(".csv"))
                    .sorted()
                    .toList();
        }
    }

    private void saveManifest() throws IOException {
        AtomicFiles.write(root.resolve("csv-library.json"), JSON.toJson(bindings));
    }

    public List<CoreTraceEvent> load(String currentServer, boolean simple) throws IOException {
        Files.createDirectories(folder);
        bindings.removeIf(
                b -> !Files.isRegularFile(folder.resolve(b.name()), LinkOption.NOFOLLOW_LINKS));
        for (Path p : files()) {
            String name = relative(p);
            if (bindings.stream().noneMatch(b -> b.name().equals(name)))
                bindings.add(new Binding(name, false, ""));
        }
        var combined = new ArrayList<CoreTraceEvent>();
        var info = new ArrayList<FileEntry>();
        var ids = new HashSet<UUID>();
        for (var b : bindings) {
            boolean selected = simple ? b.name().equals(simpleFile) : b.selected();
            try {
                var result = read(b);
                info.add(
                        new FileEntry(
                                b.name(),
                                selected,
                                result.events().size(),
                                result.skipped()
                                        + " skipped; "
                                        + result.unknownTime()
                                        + " without timestamp"));
                if (selected)
                    for (var event : result.events())
                        if (ids.add(event.context().id())) combined.add(event);
            } catch (IOException ex) {
                info.add(new FileEntry(b.name(), false, 0, ex.getMessage()));
            }
        }
        combined.sort(Comparator.comparingLong(e -> e.context().timestamp()));
        entries = List.copyOf(info);
        saveManifest();
        return List.copyOf(combined);
    }

    public void write(String name, List<CoreTraceEvent> events, String server) throws IOException {
        Files.createDirectories(folder);
        AtomicFiles.write(path(name), EventCodec.csv(events));
        invalidate(name);
        if (bindings.stream().noneMatch(b -> b.name().equals(name)))
            bindings.add(new Binding(name, true, server));
        saveManifest();
    }

    public void writeSimple(
            String name, List<CoreTraceEvent> events, String server, boolean replacePrevious)
            throws IOException {
        String previous = simpleFile;
        write(name, events, server);
        AtomicFiles.write(root.resolve("simple-csv.json"), JSON.toJson(name));
        simpleFile = name;
        if (replacePrevious && !previous.equals(name)) delete(previous);
    }

    public void select(String name, boolean selected, String server) throws IOException {
        path(name);
        for (int i = 0; i < bindings.size(); i++) {
            var b = bindings.get(i);
            if (b.name().equals(name))
                bindings.set(
                        i, new Binding(name, selected, b.server().isBlank() ? server : b.server()));
        }
        saveManifest();
    }

    public void selectAll(boolean value, String server) throws IOException {
        for (var b : List.copyOf(bindings)) select(b.name(), value, server);
    }

    public void move(String name, int delta) throws IOException {
        for (int i = 0; i < bindings.size(); i++)
            if (bindings.get(i).name().equals(name)) {
                int target = Math.clamp(i + delta, 0, bindings.size() - 1);
                Collections.swap(bindings, i, target);
                break;
            }
        saveManifest();
    }

    public void delete(String name) throws IOException {
        Path target = path(name);
        Files.deleteIfExists(target);
        pruneEmptyParents(target.getParent());
        invalidate(name);
        bindings.removeIf(b -> b.name().equals(name));
        saveManifest();
    }

    private void pruneEmptyParents(Path directory) throws IOException {
        Path base = folder.toAbsolutePath().normalize();
        while (directory != null && directory.startsWith(base) && !directory.equals(base)) {
            if (Files.isSymbolicLink(directory)) return;
            try {
                Files.delete(directory);
            } catch (DirectoryNotEmptyException ex) {
                return;
            } catch (NoSuchFileException ignored) {
            }
            directory = directory.getParent();
        }
    }

    public void deleteAll() throws IOException {
        for (Path file : files()) delete(relative(file));
    }

    public void enterSimple() throws IOException {
        if (bindings.isEmpty()) return;
        Path archive = root.resolve("csv-archive").resolve("before-simple-" + UUID.randomUUID());
        Files.createDirectories(archive);
        for (var b : List.copyOf(bindings)) {
            if (!b.name().equals(simpleFile)) {
                Path destination = archive.resolve(b.name()).normalize();
                if (!destination.startsWith(archive)) throw new IOException("Invalid archive path");
                Files.createDirectories(destination.getParent());
                Path source = path(b.name());
                Files.move(source, destination);
                pruneEmptyParents(source.getParent());
                bindings.remove(b);
                invalidate(b.name());
            }
        }
        saveManifest();
    }
}
