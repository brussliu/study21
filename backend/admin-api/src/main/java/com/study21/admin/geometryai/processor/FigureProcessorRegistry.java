package com.study21.admin.geometryai.processor;

import com.study21.admin.geometryai.dto.FigureMode;

import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * モード別プロセッサの登録所（**A〜D を独立して登録する唯一の場所**）。
 *
 * <p>Spring が集めた {@link FigureProcessor} をモードとバッチコードで引けるようにする。
 * 同じモード・同じバッチコードが 2 つ登録されていたら**起動時に落とす**
 * （どちらが使われるか分からない状態を作らない）。</p>
 *
 * <p>歴史的な {@code batC51}（モード欄が無い要求・実行履歴）は {@link FigureMode#A} として解決する。</p>
 */
@Component
public class FigureProcessorRegistry {

    private final Map<FigureMode, FigureProcessor> byMode = new EnumMap<>(FigureMode.class);
    private final Map<String, FigureProcessor> byTaskCode = new LinkedHashMap<>();

    public FigureProcessorRegistry(List<FigureProcessor> processors) {
        for (FigureProcessor processor : processors) {
            FigureProcessor existing = byMode.put(processor.mode(), processor);
            if (existing != null) {
                throw new IllegalStateException("作図モード " + processor.mode()
                        + " のプロセッサが 2 つ登録されています: "
                        + existing.getClass().getName() + " / " + processor.getClass().getName());
            }
            FigureProcessor sameCode = byTaskCode.put(processor.taskCode(), processor);
            if (sameCode != null) {
                throw new IllegalStateException("バッチコード " + processor.taskCode()
                        + " のプロセッサが 2 つ登録されています: "
                        + sameCode.getClass().getName() + " / " + processor.getClass().getName());
            }
        }
    }

    /**
     * バッチコードからモードを解決する（{@code batC51-A} → A）。AI 生図のバッチかどうかの判定にも使う。
     *
     * <p>モードが無い時代の裸の {@code batC51} は解決しない（A として扱うのは要求行の側）。</p>
     */
    public static java.util.Optional<FigureMode> modeOfTaskCode(String taskCode) {
        return FigureMode.ofTaskCode(taskCode);
    }

    /** モードのプロセッサ（A〜D が登録済みなら必ず見つかる）。 */
    public FigureProcessor of(FigureMode mode) {
        FigureProcessor processor = mode == null ? null : byMode.get(mode);
        if (processor == null) {
            throw new IllegalStateException("作図モード " + mode + " のプロセッサが登録されていません。");
        }
        return processor;
    }

    /**
     * バッチコードのプロセッサ（{@code batC51-A} ならモード A のプロセッサ）。
     *
     * <p>モードが無い時代の裸の {@code batC51} は 2026-09-19 に削除したので、空を返す
     * （{@link FigureMode#of(String)} が解決しない）。</p>
     */
    public Optional<FigureProcessor> ofTaskCode(String taskCode) {
        if (taskCode == null || taskCode.isBlank()) {
            return Optional.empty();
        }
        FigureProcessor exact = byTaskCode.get(taskCode.trim());
        if (exact != null) {
            return Optional.of(exact);
        }
        return FigureMode.of(taskCode).map(this::of);
    }

    /** 登録済みの全プロセッサ（モードの順）。 */
    public List<FigureProcessor> all() {
        return List.copyOf(byTaskCode.values());
    }

    /** 登録済みのバッチコード（モードの順）。 */
    public List<String> taskCodes() {
        return List.copyOf(byTaskCode.keySet());
    }

    /** 登録済みのモード（A〜D）。 */
    public List<FigureMode> modes() {
        return List.copyOf(byMode.keySet());
    }
}
