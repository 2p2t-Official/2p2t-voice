package org.twoptwot.voice.loader;

import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public final class KnotInjector {

    private KnotInjector() {
    }

    public static void addJar(Path jar) throws Exception {
        injectOne(jar, "payload");
        injectNestedJars(jar);
    }

    private static void injectNestedJars(Path payloadJar) throws Exception {
        Path nestDir = LoaderState.payloadDir().resolve("nested");
        Files.createDirectories(nestDir);
        int count = 0;
        try (ZipFile zip = new ZipFile(payloadJar.toFile())) {
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (entry.isDirectory()) {
                    continue;
                }
                String name = entry.getName().replace('\\', '/');
                if (!name.startsWith("META-INF/jars/") || !name.toLowerCase(Locale.ROOT).endsWith(".jar")) {
                    continue;
                }
                String fileName = name.substring(name.lastIndexOf('/') + 1);
                if (fileName.isBlank()) {
                    continue;
                }
                Path dest = nestDir.resolve(fileName);
                long expected = entry.getSize();
                boolean needsCopy = !Files.isRegularFile(dest)
                        || expected < 0
                        || Files.size(dest) != expected;
                if (needsCopy) {
                    try (InputStream in = zip.getInputStream(entry)) {
                        Files.copy(in, dest, StandardCopyOption.REPLACE_EXISTING);
                    }
                }
                injectOne(dest, "nested:" + fileName);
                count++;
            }
        }
        if (count == 0) {
            LoaderState.LOG.warn("Payload has no META-INF/jars nested jars (WebSocket may be missing)");
        } else {
            LoaderState.LOG.info("Injected {} nested jar(s) from payload", count);
        }
    }

    private static void injectOne(Path jar, String label) throws Exception {
        URL url = jar.toUri().toURL();
        List<String> tried = new ArrayList<>();
        Throwable last = null;

        ClassLoader start = KnotInjector.class.getClassLoader();
        ClassLoader context = Thread.currentThread().getContextClassLoader();

        for (ClassLoader cursor : new ClassLoader[]{start, context}) {
            if (cursor == null) {
                continue;
            }
            for (ClassLoader cl = cursor; cl != null; cl = cl.getParent()) {
                String key = cl.getClass().getName() + "@" + System.identityHashCode(cl);
                if (tried.contains(key)) {
                    continue;
                }
                tried.add(key);
                try {
                    if (tryAddUrl(cl, jar, url)) {
                        LoaderState.LOG.info("Injected {} into {}", label, cl.getClass().getName());
                        return;
                    }
                } catch (Throwable t) {
                    last = t;
                    LoaderState.LOG.warn("Inject attempt failed for {} on {}: {}", label, cl.getClass().getName(), t.toString());
                }
            }
        }

        IllegalStateException ex = new IllegalStateException(
                "Could not add " + label + " to Knot classloader (tried " + tried.size() + " loaders)", last);
        throw ex;
    }

    private static boolean tryAddUrl(ClassLoader cl, Path jar, URL url) throws Exception {
        if (invokeNamed(cl, "addUrlFwd", url)) {
            return true;
        }
        if (invokeNamed(cl, "addURL", url)) {
            return true;
        }
        if (invokeNamed(cl, "addCodeSource", jar)) {
            return true;
        }
        if (invokeNamed(cl, "addPath", jar)) {
            return true;
        }
        if (invokeNamed(cl, "addFile", jar.toFile())) {
            return true;
        }

        Object delegate = invokeNoArg(cl, "getDelegate");
        if (delegate != null) {
            if (invokeNamed(delegate, "addCodeSource", jar)) {
                return true;
            }
            if (invokeNamed(delegate, "addUrlFwd", url)) {
                return true;
            }
            if (invokeNamed(delegate, "addURL", url)) {
                return true;
            }
        }

        Object urlLoader = findFieldValue(cl, "urlLoader", "dynamicLoader", "urlClassLoader");
        if (urlLoader != null) {
            if (invokeNamed(urlLoader, "addURL", url)) {
                return true;
            }
            if (urlLoader instanceof URLClassLoader urlCl) {
                Method addURL = URLClassLoader.class.getDeclaredMethod("addURL", URL.class);
                addURL.setAccessible(true);
                addURL.invoke(urlCl, url);
                return true;
            }
        }

        if (cl instanceof URLClassLoader urlCl) {
            Method addURL = URLClassLoader.class.getDeclaredMethod("addURL", URL.class);
            addURL.setAccessible(true);
            addURL.invoke(urlCl, url);
            return true;
        }

        Field ucp = findField(cl.getClass(), "ucp", "urlClassPath", "classPath");
        if (ucp != null) {
            ucp.setAccessible(true);
            Object path = ucp.get(cl);
            if (path != null && tryAddUrlToUcp(path, url)) {
                return true;
            }
        }

        return false;
    }

    private static boolean invokeNamed(Object target, String name, Object arg) throws Exception {
        if (target == null || arg == null) {
            return false;
        }
        Class<?> argType = arg.getClass();
        for (Method method : target.getClass().getMethods()) {
            if (!method.getName().equals(name) || method.getParameterCount() != 1) {
                continue;
            }
            Class<?> param = method.getParameterTypes()[0];
            if (!param.isAssignableFrom(argType) && !isCompatible(param, arg)) {
                continue;
            }
            method.setAccessible(true);
            method.invoke(target, coerce(param, arg));
            return true;
        }
        for (Method method : target.getClass().getDeclaredMethods()) {
            if (!method.getName().equals(name) || method.getParameterCount() != 1) {
                continue;
            }
            Class<?> param = method.getParameterTypes()[0];
            if (!param.isAssignableFrom(argType) && !isCompatible(param, arg)) {
                continue;
            }
            method.setAccessible(true);
            method.invoke(target, coerce(param, arg));
            return true;
        }
        return false;
    }

    private static boolean isCompatible(Class<?> param, Object arg) {
        if (param == URL.class && arg instanceof URL) {
            return true;
        }
        if (param.getName().equals("java.nio.file.Path") && arg instanceof Path) {
            return true;
        }
        if (param == String.class && (arg instanceof Path || arg instanceof URL || arg instanceof String)) {
            return true;
        }
        if (param.getName().equals("java.io.File") && arg instanceof Path) {
            return true;
        }
        return param.isInstance(arg);
    }

    private static Object coerce(Class<?> param, Object arg) throws Exception {
        if (param.isInstance(arg)) {
            return arg;
        }
        if (param == URL.class && arg instanceof Path path) {
            return path.toUri().toURL();
        }
        if (param.getName().equals("java.nio.file.Path") && arg instanceof URL url) {
            return Path.of(url.toURI());
        }
        if (param == String.class) {
            if (arg instanceof Path path) {
                return path.toString();
            }
            if (arg instanceof URL url) {
                return Path.of(url.toURI()).toString();
            }
        }
        if (param.getName().equals("java.io.File") && arg instanceof Path path) {
            return path.toFile();
        }
        return arg;
    }

    private static Object invokeNoArg(Object target, String name) {
        try {
            Method method = target.getClass().getMethod(name);
            method.setAccessible(true);
            return method.invoke(target);
        } catch (Throwable ignored) {
        }
        try {
            Method method = target.getClass().getDeclaredMethod(name);
            method.setAccessible(true);
            return method.invoke(target);
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static Object findFieldValue(Object target, String... names) {
        Field field = findField(target.getClass(), names);
        if (field == null) {
            return null;
        }
        try {
            field.setAccessible(true);
            return field.get(target);
        } catch (Throwable t) {
            return null;
        }
    }

    private static boolean tryAddUrlToUcp(Object ucp, URL url) throws Exception {
        for (Method method : ucp.getClass().getDeclaredMethods()) {
            if (method.getParameterCount() != 1) {
                continue;
            }
            String name = method.getName();
            if (!name.equals("addURL") && !name.equals("addPath")) {
                continue;
            }
            Class<?> param = method.getParameterTypes()[0];
            method.setAccessible(true);
            if (param.isAssignableFrom(URL.class)) {
                method.invoke(ucp, url);
                return true;
            }
        }

        Field pathField = findField(ucp.getClass(), "path", "urls");
        if (pathField != null) {
            pathField.setAccessible(true);
            Object value = pathField.get(ucp);
            if (value instanceof Collection<?> collection) {
                @SuppressWarnings("unchecked")
                Collection<Object> mutable = (Collection<Object>) collection;
                if (!mutable.contains(url)) {
                    mutable.add(url);
                }
                return true;
            }
        }
        return false;
    }

    private static Field findField(Class<?> type, String... names) {
        Class<?> cursor = type;
        while (cursor != null && cursor != Object.class) {
            for (Field field : cursor.getDeclaredFields()) {
                for (String name : names) {
                    if (field.getName().equals(name)) {
                        return field;
                    }
                }
            }
            cursor = cursor.getSuperclass();
        }
        return null;
    }
}
