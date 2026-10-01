package io.github.marodriguezd.taskflow.ui.i18n;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Enforces the "exactly five languages" contract and translation completeness:
 *
 * <ul>
 *   <li>Bundle files on disk ↔ {@link Languages#SUPPORTED} are in one-to-one correspondence — a
 *       stray {@code messages_fr.properties} or {@code messages_pt.properties}, or a sixth
 *       supported tag without a file, fails this suite.
 *   <li>Every locale exposes exactly the base key set (nothing missing, nothing extra).
 *   <li>MessageFormat placeholder sets match per key across locales.
 *   <li>Representative translated values are genuinely non-English.
 *   <li>Bundle parent chains terminate in the English base bundle (deterministic fallback).
 * </ul>
 *
 * Headless: pure ResourceBundle/filesystem checks, no JavaFX.
 */
class LocaleFilesTest {

    private static final String BASE_NAME = "i18n.messages";
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{(\\d+)\\}");

    /** The complete set of bundle files allowed on disk for TaskFlow 1.1.0. */
    private static final Set<String> ALLOWED_BUNDLE_FILES =
            Set.of(
                    "messages.properties",
                    "messages_es.properties",
                    "messages_de.properties",
                    "messages_it.properties",
                    "messages_zh_Hans.properties");

    private static final ResourceBundle.Control NO_FALLBACK =
            new ResourceBundle.Control() {
                @Override
                public Locale getFallbackLocale(String bundleName, Locale locale) {
                    return null;
                }
            };

    @BeforeEach
    void setUp() {
        Messages.setLocale(Locale.ENGLISH);
    }

    @AfterEach
    void tearDown() {
        Messages.setLocale(Locale.ENGLISH);
    }

    private static ResourceBundle baseBundle() {
        return ResourceBundle.getBundle(
                BASE_NAME, Locale.ENGLISH, LocaleFilesTest.class.getClassLoader(), NO_FALLBACK);
    }

    private static ResourceBundle localeBundle(String tag) {
        return ResourceBundle.getBundle(
                BASE_NAME,
                Languages.localeOf(tag),
                LocaleFilesTest.class.getClassLoader(),
                NO_FALLBACK);
    }

    private static List<String> keysOf(ResourceBundle bundle) {
        List<String> keys = new ArrayList<>();
        Enumeration<String> enumeration = bundle.getKeys();
        while (enumeration.hasMoreElements()) {
            keys.add(enumeration.nextElement());
        }
        return keys;
    }

    private static Set<String> placeholdersOf(String pattern) {
        Set<String> found = new HashSet<>();
        Matcher matcher = PLACEHOLDER.matcher(pattern);
        while (matcher.find()) {
            found.add(matcher.group(1));
        }
        return found;
    }

    @Test
    @DisplayName("Exactly 5 bundle files exist on disk — no French, no Portuguese, no extras")
    void testBundleFileBijection() throws IOException, URISyntaxException {
        URL i18nDir = getClass().getResource("/i18n");
        assertThat(i18nDir).as("i18n resource directory").isNotNull();
        assertThat(i18nDir.getProtocol()).isEqualTo("file");

        Set<String> found = new HashSet<>();
        try (Stream<Path> files = Files.list(Path.of(i18nDir.toURI()))) {
            files.map(p -> p.getFileName().toString())
                    .filter(name -> name.startsWith("messages") && name.endsWith(".properties"))
                    .forEach(found::add);
        }
        assertThat(found)
                .as("bundle files on disk (exactly 5 supported languages)")
                .containsExactlyInAnyOrderElementsOf(ALLOWED_BUNDLE_FILES)
                .hasSize(5);
        // Explicit contract statements (also fail if someone adds fr/pt alongside a rename)
        assertThat(found).doesNotContain("messages_fr.properties", "messages_pt.properties");
    }

    @Test
    @DisplayName("Every locale exposes exactly the base key set")
    void testExactKeyParity() {
        List<String> baseKeys = keysOf(baseBundle());
        for (String tag : Languages.SUPPORTED) {
            if (Languages.EN.equals(tag)) {
                continue; // English IS the base bundle (no messages_en.properties by design)
            }
            ResourceBundle bundle = localeBundle(tag);
            assertThat(bundle.getLocale())
                    .as("bundle for %s resolves to its own file, not the base", tag)
                    .isEqualTo(Languages.localeOf(tag));
            assertThat(keysOf(bundle))
                    .as("key set of locale '%s'", tag)
                    .containsExactlyInAnyOrderElementsOf(baseKeys);
        }
    }

    @Test
    @DisplayName("MessageFormat placeholder sets match per key across all locales")
    void testPlaceholderParity() {
        ResourceBundle base = baseBundle();
        for (String key : keysOf(base)) {
            Set<String> basePlaceholders = placeholdersOf(base.getString(key));
            for (String tag : Languages.SUPPORTED) {
                if (Languages.EN.equals(tag)) {
                    continue;
                }
                String translated = localeBundle(tag).getString(key);
                assertThat(placeholdersOf(translated))
                        .as("placeholders of key '%s' in locale '%s'", key, tag)
                        .isEqualTo(basePlaceholders);
                assertThat(translated).as("value of '%s' in '%s'", key, tag).isNotBlank();
            }
        }
    }

    @Test
    @DisplayName("Representative values are genuinely translated (not English copies)")
    void testTranslationSentinels() {
        ResourceBundle es = localeBundle(Languages.ES);
        assertThat(es.getString("dialog.cancel")).isEqualTo("Cancelar");
        assertThat(es.getString("priority.high")).isEqualTo("Alta");
        assertThat(es.getString("footer.newTask")).isEqualTo("＋  Nueva tarea");

        ResourceBundle de = localeBundle(Languages.DE);
        assertThat(de.getString("dialog.cancel")).isEqualTo("Abbrechen");
        assertThat(de.getString("history.restore")).isEqualTo("Wiederherstellen");
        assertThat(de.getString("header.tasks.other")).isEqualTo("{0} Aufgaben");

        ResourceBundle it = localeBundle(Languages.IT);
        assertThat(it.getString("dialog.cancel")).isEqualTo("Annulla");
        assertThat(it.getString("empty.title")).isEqualTo("Nessuna attività ancora");

        ResourceBundle zh = localeBundle(Languages.ZH_HANS);
        assertThat(zh.getString("dialog.cancel")).isEqualTo("取消");
        assertThat(zh.getString("empty.title")).isEqualTo("暂无任务");
        // Plural forms intentionally identical in Simplified Chinese
        assertThat(zh.getString("header.tasks.one")).isEqualTo(zh.getString("header.tasks.other"));
    }

    @Test
    @DisplayName("Key missing from a locale bundle resolves to the English parent bundle")
    void testKeyLevelEnglishFallback() {
        // Test-only fixtures under src/test/resources: the German file intentionally lacks
        // 'only.in.base', so the parent-chain fallback to the English base is directly observable.
        ResourceBundle de =
                ResourceBundle.getBundle(
                        "i18n_fallback.messages",
                        Languages.localeOf(Languages.DE),
                        LocaleFilesTest.class.getClassLoader(),
                        NO_FALLBACK);
        assertThat(de.getLocale()).isEqualTo(Languages.localeOf(Languages.DE));
        assertThat(de.getString("shared.key")).isEqualTo("Gemeinsam");
        // Missing in German -> served by the English base parent bundle, deterministically
        assertThat(de.containsKey("only.in.base")).isTrue();
        assertThat(de.getString("only.in.base")).isEqualTo("English only");
    }

    @Test
    @DisplayName("Locales without any bundle file (fr, pt, ja) resolve to the English base")
    void testUnsupportedLocaleResolvesToEnglishBase() {
        for (String tag : new String[] {"fr", "pt", "ja"}) {
            ResourceBundle bundle = localeBundle(tag);
            // No messages_fr/pt/ja file exists -> the base bundle (English) is returned.
            // The base bundle reports no locale (null on some JDKs, Locale.ROOT on others).
            assertThat(isBaseLocale(bundle.getLocale()))
                    .as("bundle locale for unsupported '%s'", tag)
                    .isTrue();
            assertThat(bundle.getString("dialog.cancel")).isEqualTo("Cancel");
        }
        // English itself is served by the base bundle (no messages_en.properties by design)
        assertThat(isBaseLocale(localeBundle(Languages.EN).getLocale())).isTrue();
    }

    private static boolean isBaseLocale(Locale locale) {
        return locale == null || Locale.ROOT.equals(locale);
    }

    @Test
    @DisplayName("Locale-aware Messages lookups return per-locale values with English fallback")
    void testMessagesPerLocale() {
        assertThat(Messages.get("dialog.cancel")).isEqualTo("Cancel");
        Messages.setLocale(Languages.localeOf(Languages.DE));
        assertThat(Messages.get("dialog.cancel")).isEqualTo("Abbrechen");
        assertThat(Messages.count("header.tasks", 1)).isEqualTo("1 Aufgabe");
        assertThat(Messages.count("header.tasks", 2)).isEqualTo("2 Aufgaben");
        Messages.setLocale(Languages.localeOf(Languages.ZH_HANS));
        assertThat(Messages.count("header.tasks", 1)).isEqualTo("1 个任务");
        assertThat(Messages.get("header.tasks.other", "5")).isEqualTo("5 个任务");
        Messages.setLocale(Languages.localeOf(Languages.ES));
        assertThat(Messages.count("header.tasks", 1)).isEqualTo("1 tarea");
        Messages.setLocale(Languages.localeOf(Languages.IT));
        assertThat(Messages.count("header.tasks", 0)).isEqualTo("0 attività");
        // Unknown key still deterministic in any locale
        assertThat(Messages.get("no.such.key")).isEqualTo("no.such.key");
    }
}
