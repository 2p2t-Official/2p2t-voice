package org.twoptwot.voice.loader;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.Collection;

public final class KnotInjector {

    private KnotInjector() {
    }

    public static void addJar(Path jar) throws Exception {
        URL url = jar.toUri().toURL();
        ClassLoader cl = KnotInjector.class.getClassLoader();
        Throwable last = null;

        for (ClassLoader cursor = cl; cursor != null; cursor = cursor.getParent()) {
            try {
                if (tryAddUrl(cursor, url)) {
                    LoaderState.LOG.info("Injected payload into {}", cursor.getClass().getName());
                    return;
                }
            } catch (Throwable t) {
                last = t;
            }
        }

        ClassLoader context = Thread.currentThread().getContextClassLoader();
        if (context != null && context != cl) {
            try {
                if (tryAddUrl(context, url)) {
                    LoaderState.LOG.info("Injected payload into context {}", context.getClass().getName());
                    return;
                }
            } catch (Throwable t) {
                last = t;
            }
        }

        throw new IllegalStateException("Could not add jar to Knot classloader", last);
    }

    private static boolean tryAddUrl(ClassLoader cl, URL url) throws Exception {
        if (cl instanceof URLClassLoader urlCl) {
            Method addURL = URLClassLoader.class.getDeclaredMethod("addURL", URL.class);
            addURL.setAccessible(true);
            addURL.invoke(urlCl, url);
            return true;
        }

        for (String methodName : new String[]{"addURL", "addPath", "addCodeSource", "addFile"}) {
            for (Method method : cl.getClass().getDeclaredMethods()) {
                if (!method.getName().equals(methodName) || method.getParameterCount() != 1) {
                    continue;
                }
                Class<?> param = method.getParameterTypes()[0];
                method.setAccessible(true);
                if (param.isAssignableFrom(URL.class)) {
                    method.invoke(cl, url);
                    return true;
                }
                if (param.getName().equals("java.nio.file.Path")) {
                    method.invoke(cl, Path.of(url.toURI()));
                    return true;
                }
                if (param == String.class) {
                    method.invoke(cl, Path.of(url.toURI()).toString());
                    return true;
                }
            }
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
