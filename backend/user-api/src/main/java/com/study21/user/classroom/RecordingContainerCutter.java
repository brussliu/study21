package com.study21.user.classroom;

/**
 * 録音の分塊から**再生用の 1 本**を組立てるための、コンテナの切れ目の判定。
 *
 * <p>同じ `MediaRecorder` が `timeslice` で切った分塊は、1 本のストリームの途中なので
 * そのまま繋げば 1 本のコンテナになる（1 塊目だけが EBML ヘッダを持つ）。ところが
 * **一時停止・画面の開き直しでは録音を作り直す**ので、2 本目の分塊は**コンテナのヘッダから
 * 始まる**（EBML ヘッダ → Segment → Info → Tracks → Cluster …）。単純に連結すると
 * ファイルの途中にもう 1 つヘッダが入り、プレイヤーはそこで**最初の回の音しか再生しない**
 * （実測で確認した不具合）。</p>
 *
 * <p>そこで、2 本目以降の分塊は**最初の Cluster までを落として**中身だけを繋ぐ
 * （EBML は Cluster の並びなので、これは再符号化なしの再多重化にあたる）。
 * 分塊そのものは 1 塊 1 ファイルで残すので、組立てに失敗しても音は失われない。</p>
 *
 * <p>判定できないときは<b>勝手に連結しない</b>: {@link #NOT_CUT} を返し、呼び側が理由を
 * 記録する（「連結できたはず」と黙って思い込まない）。</p>
 */
public final class RecordingContainerCutter {

    /** そのまま繋いでよい（同じコンテナの続き・規格で連結できる Ogg）。 */
    public static final long CONTINUE = 0;
    /** 新しいコンテナのヘッダだが、落とす位置が分からない（呼び側がログに残す）。 */
    public static final long NOT_CUT = -1;

    private static final long ID_EBML = 0x1A45DFA3L;
    private static final long ID_SEGMENT = 0x18538067L;
    private static final long ID_CLUSTER = 0x1F43B675L;
    /** Cluster の ID バイト列（EBML の解析に失敗したときの最終手段）。 */
    private static final byte[] CLUSTER_BYTES = {0x1F, 0x43, (byte) 0xB6, 0x75};

    private RecordingContainerCutter() {
    }

    /**
     * この分塊の中身を**何バイト目から**繋げばよいかを返す。
     *
     * @param head  分塊の先頭（ファイル全部でも、先頭の一部でもよい）
     * @param length 有効な長さ
     * @return {@link #CONTINUE}（そのまま繋ぐ）/ 落とすバイト数 / {@link #NOT_CUT}
     */
    public static long bodyOffset(byte[] head, int length) {
        if (head == null || length <= 0) {
            return CONTINUE;
        }
        if (isWebm(head, length)) {
            long walk = webmBodyOffset(head, length);
            if (walk > 0) {
                return walk;
            }
            // 解析で辿れない（大きさの分からない要素など）: Cluster の ID を探す
            long scan = scanForCluster(head, length);
            return scan > 0 ? scan : NOT_CUT;
        }
        if (isOgg(head, length)) {
            // Ogg は「連結した論理ビットストリーム（chained Ogg）」が規格で認められている
            return CONTINUE;
        }
        if (isMp4(head, length)) {
            // MP4 は連結できない（再多重化が要る）。勝手に繋がない
            return NOT_CUT;
        }
        // ヘッダが無い＝同じコンテナの続き（timeslice で切れた 2 塊目以降）
        return CONTINUE;
    }

    /** この分塊が**新しいコンテナの先頭**か（EBML / Ogg / MP4 のヘッダで始まるか）。 */
    public static boolean isContainerHead(byte[] head, int length) {
        return isWebm(head, length) || isOgg(head, length) || isMp4(head, length);
    }

    public static boolean isWebm(byte[] head, int length) {
        return length >= 4 && (head[0] & 0xFF) == 0x1A && (head[1] & 0xFF) == 0x45
                && (head[2] & 0xFF) == 0xDF && (head[3] & 0xFF) == 0xA3;
    }

    public static boolean isOgg(byte[] head, int length) {
        return length >= 4 && head[0] == 'O' && head[1] == 'g' && head[2] == 'g' && head[3] == 'S';
    }

    public static boolean isMp4(byte[] head, int length) {
        return length >= 8 && head[4] == 'f' && head[5] == 't' && head[6] == 'y' && head[7] == 'p';
    }

    // ------------------------------------------------------------------ 内部

    /**
     * EBML（webm）の先頭から、Segment の子要素を順に辿って**最初の Cluster の位置**を返す。
     *
     * <p>Cluster より前（EBML ヘッダ・Segment・SeekHead・Info・Tracks）は落とす。
     * 大きさの分からない要素に出会ったら辿れないので {@link #NOT_CUT}（呼び側が別手段へ）。</p>
     */
    private static long webmBodyOffset(byte[] bytes, int length) {
        ElementHeader ebml = header(bytes, 0, length);
        if (ebml == null || ebml.id != ID_EBML || ebml.unknownSize) {
            return NOT_CUT;
        }
        long afterEbml = ebml.dataStart + ebml.dataSize;
        if (afterEbml < 0 || afterEbml >= length) {
            // ヘッダが読込み窓より大きい（あり得ないが、決め打ちしない）
            return NOT_CUT;
        }
        ElementHeader segment = header(bytes, (int) afterEbml, length);
        if (segment == null || segment.id != ID_SEGMENT) {
            return NOT_CUT;
        }
        long position = segment.dataStart + (segment.unknownSize ? 0 : segment.dataSize);
        while (position < length) {
            ElementHeader child = header(bytes, (int) position, length);
            if (child == null) {
                return NOT_CUT;
            }
            if (child.id == ID_CLUSTER) {
                return position;
            }
            if (child.unknownSize) {
                return NOT_CUT;
            }
            long next = child.dataStart + child.dataSize;
            if (next <= position || next > length) {
                return NOT_CUT;
            }
            position = next;
        }
        return NOT_CUT;
    }

    /** Cluster の ID バイト列を探す（EBML の解析に失敗したときの最終手段）。 */
    private static long scanForCluster(byte[] bytes, int length) {
        for (int index = 4; index + CLUSTER_BYTES.length <= length; index += 1) {
            boolean found = true;
            for (int offset = 0; offset < CLUSTER_BYTES.length; offset += 1) {
                if (bytes[index + offset] != CLUSTER_BYTES[offset]) {
                    found = false;
                    break;
                }
            }
            if (found) {
                return index;
            }
        }
        return NOT_CUT;
    }

    /** 要素のヘッダ（ID・大きさ・中身の開始位置）。 */
    private record ElementHeader(long id, long dataStart, long dataSize, boolean unknownSize) {
    }

    /** 位置 position の要素ヘッダを読む（読めなければ null）。 */
    private static ElementHeader header(byte[] bytes, int position, int length) {
        if (position < 0 || position >= length) {
            return null;
        }
        int idLength = vintLength(bytes[position] & 0xFF);
        if (idLength < 1 || idLength > 4 || position + idLength > length) {
            return null;
        }
        long id = 0;
        for (int index = 0; index < idLength; index += 1) {
            id = (id << 8) | (bytes[position + index] & 0xFF);
        }
        int sizePosition = position + idLength;
        if (sizePosition >= length) {
            return null;
        }
        int sizeLength = vintLength(bytes[sizePosition] & 0xFF);
        if (sizeLength < 1 || sizeLength > 8 || sizePosition + sizeLength > length) {
            return null;
        }
        // 先頭バイトのマーカービットを落として、残りを続けて読む
        long size = (bytes[sizePosition] & 0xFF) & (0xFF >> sizeLength);
        for (int index = 1; index < sizeLength; index += 1) {
            size = (size << 8) | (bytes[sizePosition + index] & 0xFF);
        }
        // 値のビットが全部 1 = 大きさ未定（ライブ配信の webm で使われる）
        boolean unknown = size == (1L << (7 * sizeLength)) - 1;
        return new ElementHeader(id, sizePosition + sizeLength, size, unknown);
    }

    /** 先頭バイトから可変長整数の長さ（1〜8）を返す（0 なら不正）。 */
    private static int vintLength(int firstByte) {
        for (int index = 0; index < 8; index += 1) {
            if ((firstByte & (0x80 >> index)) != 0) {
                return index + 1;
            }
        }
        return 0;
    }
}
