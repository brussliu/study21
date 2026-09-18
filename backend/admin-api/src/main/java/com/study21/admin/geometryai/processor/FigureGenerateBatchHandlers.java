package com.study21.admin.geometryai.processor;

import com.study21.admin.batch.BatchExecutionEntity;
import com.study21.admin.batch.BatchTaskHandler;
import com.study21.admin.geometryai.AiFigureGenerateStep;
import com.study21.admin.geometryai.dto.FigureMode;
import org.springframework.stereotype.Component;

/**
 * モード別のバッチハンドラ（{@code batC51-A}〜{@code batC51-D}）。
 *
 * <p>モードごとに**独立して登録**する（バッチ一覧・必須設定・AI 呼出履歴のバッチコードが
 * モードごとに見える）。実装は共通の {@link AiFigureGenerateStep} にモードを渡すだけで、
 * 画像の取込・モデル呼び出し・検証・記録は共通の部品が行う。</p>
 *
 * <p>入れ子の static クラスにしているのは、4 つが同じ形（モードだけが違う）だから。
 * Spring は入れ子の static クラスもコンポーネントとして拾う。</p>
 */
public final class FigureGenerateBatchHandlers {

    private FigureGenerateBatchHandlers() {
    }

    /** batC51-A（画像をもとに再現）。 */
    @Component
    public static class A implements BatchTaskHandler {

        private final AiFigureGenerateStep step;

        public A(AiFigureGenerateStep step) {
            this.step = step;
        }

        @Override
        public String taskCode() {
            return FigureMode.A.taskCode();
        }

        @Override
        public String execute(BatchExecutionEntity execution) {
            return String.valueOf(step.run(execution, FigureMode.A).get("message"));
        }
    }

    /** batC51-B（数式からグラフを作成）。 */
    @Component
    public static class B implements BatchTaskHandler {

        private final AiFigureGenerateStep step;

        public B(AiFigureGenerateStep step) {
            this.step = step;
        }

        @Override
        public String taskCode() {
            return FigureMode.B.taskCode();
        }

        @Override
        public String execute(BatchExecutionEntity execution) {
            return String.valueOf(step.run(execution, FigureMode.B).get("message"));
        }
    }

    /** batC51-C（文章の条件から作図）。 */
    @Component
    public static class C implements BatchTaskHandler {

        private final AiFigureGenerateStep step;

        public C(AiFigureGenerateStep step) {
            this.step = step;
        }

        @Override
        public String taskCode() {
            return FigureMode.C.taskCode();
        }

        @Override
        public String execute(BatchExecutionEntity execution) {
            return String.valueOf(step.run(execution, FigureMode.C).get("message"));
        }
    }

    /** batC51-D（文章と図を合わせて作図）。 */
    @Component
    public static class D implements BatchTaskHandler {

        private final AiFigureGenerateStep step;

        public D(AiFigureGenerateStep step) {
            this.step = step;
        }

        @Override
        public String taskCode() {
            return FigureMode.D.taskCode();
        }

        @Override
        public String execute(BatchExecutionEntity execution) {
            return String.valueOf(step.run(execution, FigureMode.D).get("message"));
        }
    }
}
