package com.study21.user.tempfile;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.sql.Timestamp;
import java.util.List;

@Mapper
public interface TempFileMapper {
    List<Long> findFamilyStudentIds(@Param("accountId") long accountId, @Param("accountType") String accountType);

    List<TempFileEntity> search(@Param("familyStudentId") long familyStudentId,
                                @Param("keyword") String keyword,
                                @Param("imageOnly") Boolean imageOnly,
                                @Param("registeredSince") Timestamp registeredSince,
                                @Param("registeredBefore") Timestamp registeredBefore,
                                @Param("limit") int limit);

    TempFileEntity findById(@Param("familyStudentId") long familyStudentId, @Param("tempFileId") long tempFileId);

    int insert(TempFileEntity entity);

    int updateMeta(@Param("familyStudentId") long familyStudentId, @Param("tempFileId") long tempFileId,
                   @Param("originalFileName") String originalFileName, @Param("comment") String comment,
                   @Param("operator") String operator);

    int updateImage(@Param("familyStudentId") long familyStudentId, @Param("tempFileId") long tempFileId,
                    @Param("originalFileName") String originalFileName, @Param("storedFileName") String storedFileName,
                    @Param("extension") String extension, @Param("mimeType") String mimeType,
                    @Param("fileSize") long fileSize, @Param("path") String path,
                    @Param("thumbnail500") String thumbnail500, @Param("thumbnail200") String thumbnail200,
                    @Param("thumbnail50") String thumbnail50, @Param("comment") String comment,
                    @Param("operator") String operator);

    int deleteById(@Param("familyStudentId") long familyStudentId, @Param("tempFileId") long tempFileId);

    int deleteByIds(@Param("familyStudentId") long familyStudentId, @Param("ids") List<Long> ids);
}
