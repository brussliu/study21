package com.study21.user.reading;

import redis.embedded.RedisServer;

import java.io.IOException;
import java.net.ServerSocket;

/**
 * 読書管理の HTTP テストが使う検証用の Redis（JVM に 1 つだけ）。
 *
 * <p>複数のテストクラス（{@link ReadingHttpApiTest} / {@link ReadingLookupApiTest}）が
 * 同じ Spring コンテキスト基盤を共有するので、`@AfterAll` でクラスごとに止めると
 * 2 つ目のクラスがログインできなくなる。ここで**遅延して 1 つだけ起動**し、
 * 停止は JVM のシャットダウンフックに任せる（埋め込み Redis は子プロセスを残さない）。</p>
 *
 * <p>DB のパスワードが無い（＝テストがスキップされる）ときは起動しない。</p>
 */
final class EmbeddedTestRedis {

    private static final String URL = "127.0.0.1";
    private static final int PORT = findFreePort();
    private static final boolean STARTED = startIfEnabled();

    private EmbeddedTestRedis() {
    }

    static String host() {
        return URL;
    }

    static int port() {
        return PORT;
    }

    private static boolean startIfEnabled() {
        String password = System.getenv("STUDY21_DATASOURCE_PASSWORD");
        if (password == null || password.isBlank()) {
            return false;
        }
        try {
            RedisServer server = RedisServer.newRedisServer().port(PORT).build();
            server.start();
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                try {
                    server.stop();
                } catch (IOException ignored) {
                    // 終了時なので握りつぶす
                }
            }));
            return true;
        } catch (IOException cause) {
            throw new IllegalStateException("検証用の Redis を起動できませんでした。", cause);
        }
    }

    /** 起動できたか（診断用）。 */
    static boolean started() {
        return STARTED;
    }

    private static int findFreePort() {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        } catch (IOException cause) {
            throw new IllegalStateException("空きポートを取得できませんでした。", cause);
        }
    }
}
