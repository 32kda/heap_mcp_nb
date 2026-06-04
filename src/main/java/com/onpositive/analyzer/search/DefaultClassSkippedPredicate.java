package com.onpositive.analyzer.search;

import java.util.List;

public class DefaultClassSkippedPredicate implements ClassSkippedPredicate {

    private static final List<String> SKIP_PREFIXES = List.of(
            "java.", "javax.", "sun.", "jdk.", "oracle.", "com.sun.", "netscape.",
            "org.apache.", "org.springframework.", "com.springsource.",
            "org.hibernate.", "org.jboss.", "org.wildfly.",
            "org.eclipse.", "com.google.", "com.fasterxml.",
            "io.netty.", "reactor.core.", "reactor.netty.",
            "org.eclipse.microprofile.", "io.micrometer.", "io.micronaut.",
            "io.quarkus.", "lombok.", "org.projectlombok.",
            "org.objectweb.asm.", "org.ow2.asm.", "net.sf.cglib.", "cglib.",
            "javassist.", "org.javassist.",
            "org.slf4j.", "ch.qos.logback.", "org.apache.logging.", "org.apache.log4j.",
            "org.junit.", "org.testng.", "org.mockito.", "org.assertj.",
            "org.hamcrest.", "org.jmock.", "org.easymock.",
            "com.thoughtworks.xstream.", "org.codehaus.", "com.esotericsoftware.",
            "org.bouncycastle.", "org.yaml.",
            "com.fasterxml.jackson.dataformat.yaml.",
            "org.glassfish.", "org.jvnet.",
            "io.undertow.", "rx.", "org.joda.",
            "org.apache.tomcat.", "org.apache.catalina.", "org.apache.coyote.",
            "org.eclipse.jetty."
    );

    @Override
    public boolean shouldSkip(String fullyQualifiedClassName) {
        if (fullyQualifiedClassName == null || fullyQualifiedClassName.isEmpty()) {
            return true;
        }
        for (String prefix : SKIP_PREFIXES) {
            if (fullyQualifiedClassName.startsWith(prefix)) {
                return true;
            }
        }
        if (isSyntheticProxy(fullyQualifiedClassName)) {
            return true;
        }
        if (fullyQualifiedClassName.contains("$Lambda")) {
            return true;
        }
        return endsWithDollarDigits(fullyQualifiedClassName);
    }

    private static boolean isSyntheticProxy(String name) {
        if (name.contains("$$")) return true;
        if (name.endsWith("_Stub")) return true;
        if (name.endsWith("__Impl")) return true;
        if (name.contains("$HibernateProxy$")) return true;
        if (startsWithDollarFollowedByLetter(name)) return true;
        return isDollarProxySuffix(name);
    }

    private static boolean startsWithDollarFollowedByLetter(String name) {
        if (name.isEmpty() || name.charAt(0) != '$') return false;
        int i = 0;
        while (i < name.length() && name.charAt(i) == '$') i++;
        return i < name.length() && Character.isLetter(name.charAt(i));
    }

    private static boolean endsWithDollarDigits(String name) {
        int idx = name.lastIndexOf('$');
        if (idx < 0 || idx == name.length() - 1) return false;
        for (int i = idx + 1; i < name.length(); i++) {
            if (!Character.isDigit(name.charAt(i))) return false;
        }
        return true;
    }

    private static boolean isDollarProxySuffix(String name) {
        int idx = name.lastIndexOf("$Proxy");
        if (idx < 0) return false;
        String after = name.substring(idx + "$Proxy".length());
        if (after.isEmpty()) return true;
        for (int i = 0; i < after.length(); i++) {
            if (!Character.isDigit(after.charAt(i))) return false;
        }
        return true;
    }
}
