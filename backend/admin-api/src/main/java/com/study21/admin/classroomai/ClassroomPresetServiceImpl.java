package com.study21.admin.classroomai;

import com.study21.common.core.exception.NotFoundException;
import com.study21.common.core.exception.ValidationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.util.List;

/**
 * 前置詞プリセット（GLOBAL スコープ）の管理。設定画面の専用サブパネルが使う。
 *
 * <p>管理するのは **GLOBAL スコープだけ**（他スコープは user-api が家庭/学生スコープで持つ）。
 * GLOBAL 以外の ID を触ろうとしたら 404（存在を漏らさない）。削除は FK
 * （{@code CR_授業記録情報.前置詞ID → ON DELETE SET NULL}）が既存記録の前置詞ID を NULL 化するので、
 * **過去の授業は消さない**（前置詞テキストのスナップショットも残る）。</p>
 */
@Service
public class ClassroomPresetServiceImpl implements ClassroomPresetService {

    private static final Logger log = LoggerFactory.getLogger(ClassroomPresetServiceImpl.class);

    private final ClassroomPresetMapper presetMapper;

    public ClassroomPresetServiceImpl(ClassroomPresetMapper presetMapper) {
        this.presetMapper = presetMapper;
    }

    @Override
    @Transactional(readOnly = true)
    public List<ClassroomPresetModels.PresetView> list() {
        return presetMapper.findGlobal().stream().map(ClassroomPresetServiceImpl::toView).toList();
    }

    @Override
    @Transactional
    public ClassroomPresetModels.PresetView create(ClassroomPresetModels.SaveRequest request) {
        ClassroomPresetEntity entity = new ClassroomPresetEntity();
        entity.setScope("GLOBAL");
        entity.setCreatedBy(null);
        entity.setName(requireName(request.name()));
        entity.setText(request.text() == null ? null : request.text().trim());
        entity.setDisplayOrder(normalizeOrder(request.displayOrder()));
        presetMapper.insert(entity);
        log.info("classroom preset created. presetId={} name={} order={}",
                entity.getPresetId(), entity.getName(), entity.getDisplayOrder());
        return toView(presetMapper.findById(entity.getPresetId()));
    }

    @Override
    @Transactional
    public ClassroomPresetModels.PresetView update(long presetId, ClassroomPresetModels.SaveRequest request) {
        ClassroomPresetEntity entity = requireGlobal(presetId);
        entity.setName(requireName(request.name()));
        entity.setText(request.text() == null ? null : request.text().trim());
        entity.setDisplayOrder(normalizeOrder(request.displayOrder()));
        if (presetMapper.updateGlobal(entity) == 0) {
            throw new NotFoundException("前置詞プリセットが見つかりません。");
        }
        log.info("classroom preset updated. presetId={} name={} order={}",
                presetId, entity.getName(), entity.getDisplayOrder());
        return toView(presetMapper.findById(presetId));
    }

    @Override
    @Transactional
    public ClassroomPresetModels.DeleteResult delete(long presetId) {
        ClassroomPresetEntity entity = requireGlobal(presetId);
        if (presetMapper.deleteGlobal(presetId) == 0) {
            throw new NotFoundException("前置詞プリセットが見つかりません。");
        }
        log.info("classroom preset deleted. presetId={} name={}", presetId, entity.getName());
        return new ClassroomPresetModels.DeleteResult(presetId, "前置詞プリセットを削除しました。"
                + "（過去の授業記録は残り、前置詞ID だけが空になります）");
    }

    /** GLOBAL の行だけを返す（他スコープ・存在しない行は 404。存在も漏らさない）。 */
    private ClassroomPresetEntity requireGlobal(long presetId) {
        ClassroomPresetEntity entity = presetMapper.findById(presetId);
        if (entity == null || !"GLOBAL".equals(entity.getScope())) {
            throw new NotFoundException("前置詞プリセットが見つかりません。");
        }
        return entity;
    }

    /** 名前は必須（前後の空白は取り除く。空・200 文字超は 400）。 */
    private static String requireName(String name) {
        String value = name == null ? "" : name.trim();
        if (value.isEmpty()) {
            throw new ValidationException("プリセット名を入力してください。");
        }
        if (value.length() > 200) {
            throw new ValidationException("プリセット名は200文字以内で入力してください。");
        }
        return value;
    }

    /** 表示順（未指定は 0。負値は 0 に丸める）。 */
    private static int normalizeOrder(Integer displayOrder) {
        return displayOrder == null || displayOrder < 0 ? 0 : displayOrder;
    }

    private static ClassroomPresetModels.PresetView toView(ClassroomPresetEntity entity) {
        return new ClassroomPresetModels.PresetView(entity.getPresetId(), entity.getScope(), entity.getName(),
                entity.getText(), entity.getDisplayOrder() == null ? 0 : entity.getDisplayOrder(),
                iso(entity.getCreatedAt()), iso(entity.getUpdatedAt()));
    }

    private static String iso(Timestamp value) {
        return value == null ? null : value.toLocalDateTime().toString();
    }
}
