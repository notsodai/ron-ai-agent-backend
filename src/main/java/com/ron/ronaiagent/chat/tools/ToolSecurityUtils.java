package com.ron.ronaiagent.chat.tools;

import cn.hutool.core.util.StrUtil;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.Set;

/**
 * Shared security guards for file/network tools.
 */
public final class ToolSecurityUtils {

    private static final Set<String> ALLOWED_SCHEMES = Set.of("http", "https");

    private ToolSecurityUtils() {
    }

    public static Path resolveSafePath(String baseDir, String fileName) {
        if (StrUtil.isBlank(fileName)) {
            throw new IllegalArgumentException("File name cannot be empty");
        }

        String normalizedFileName = fileName.trim();
        if (normalizedFileName.contains("..") || normalizedFileName.contains("/") || normalizedFileName.contains("\\")) {
            throw new IllegalArgumentException("Invalid file name");
        }

        Path basePath = Paths.get(baseDir).toAbsolutePath().normalize();
        Path targetPath = basePath.resolve(normalizedFileName).normalize();
        if (!targetPath.startsWith(basePath)) {
            throw new IllegalArgumentException("Invalid target path");
        }
        return targetPath;
    }

    public static URI validatePublicHttpUrl(String rawUrl) {
        if (StrUtil.isBlank(rawUrl)) {
            throw new IllegalArgumentException("URL cannot be empty");
        }
        try {
            URI uri = new URI(rawUrl.trim());
            String scheme = StrUtil.nullToEmpty(uri.getScheme()).toLowerCase(Locale.ROOT);
            if (!ALLOWED_SCHEMES.contains(scheme)) {
                throw new IllegalArgumentException("Only HTTP/HTTPS URLs are allowed");
            }
            String host = uri.getHost();
            if (StrUtil.isBlank(host)) {
                throw new IllegalArgumentException("Invalid URL host");
            }
            if (isLocalHost(host) || isPrivateAddress(host)) {
                throw new IllegalArgumentException("Local/internal network addresses are not allowed");
            }
            return uri;
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("Invalid URL format", e);
        }
    }

    private static boolean isLocalHost(String host) {
        String h = host.toLowerCase(Locale.ROOT);
        return "localhost".equals(h) || h.endsWith(".localhost");
    }

    private static boolean isPrivateAddress(String host) {
        try {
            InetAddress[] addresses = InetAddress.getAllByName(host);
            for (InetAddress address : addresses) {
                if (address.isAnyLocalAddress()
                        || address.isLoopbackAddress()
                        || address.isSiteLocalAddress()
                        || address.isLinkLocalAddress()
                        || address.isMulticastAddress()) {
                    return true;
                }
                if (address instanceof Inet4Address inet4 && isPrivateIpv4(inet4.getAddress())) {
                    return true;
                }
                if (address instanceof Inet6Address inet6 && isUniqueLocalIpv6(inet6.getAddress())) {
                    return true;
                }
            }
            return false;
        } catch (Exception e) {
            throw new IllegalArgumentException("Unable to resolve host: " + host, e);
        }
    }

    private static boolean isPrivateIpv4(byte[] bytes) {
        int b0 = bytes[0] & 0xFF;
        int b1 = bytes[1] & 0xFF;
        return b0 == 10
                || (b0 == 172 && b1 >= 16 && b1 <= 31)
                || (b0 == 192 && b1 == 168)
                || (b0 == 127)
                || (b0 == 169 && b1 == 254);
    }

    private static boolean isUniqueLocalIpv6(byte[] bytes) {
        int first = bytes[0] & 0xFF;
        return (first & 0xFE) == 0xFC;
    }
}
