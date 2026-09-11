package com.study21.user.testinfo;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;

@Mapper
public interface TestInfoMapper {
    List<Long> findFamilyStudentIds(@Param("accountId") long accountId, @Param("accountType") String accountType);

    List<TestInfoEntity> searchTestInfos(@Param("familyStudentId") long familyStudentId,
                                         @Param("subject") String subject, @Param("kind") String kind,
                                         @Param("keyword") String keyword);
    TestInfoEntity findTestInfo(@Param("familyStudentId") long familyStudentId, @Param("testNo") String testNo);
    /** ファイル件数制限の検査中に、同じテストへの同時追加を防ぐための行ロック。 */
    int lockTestInfo(@Param("familyStudentId") long familyStudentId, @Param("testNo") String testNo);
    int countTestNo(@Param("familyStudentId") long familyStudentId, @Param("testNo") String testNo);
    int insertTestInfo(TestInfoEntity test);
    int updateTestInfo(@Param("familyStudentId") long familyStudentId, @Param("testNo") String testNo,
                       @Param("beforeVersion") int beforeVersion, @Param("testName") String testName,
                       @Param("subject") String subject, @Param("kind") String kind,
                       @Param("examDate") LocalDate examDate, @Param("score") Integer score,
                       @Param("fullScore") Integer fullScore, @Param("memo") String memo,
                       @Param("operator") String operator);
    int deleteTestInfo(@Param("familyStudentId") long familyStudentId, @Param("testNo") String testNo);

    List<TestFileEntity> findTestFilesByTestIds(@Param("familyStudentId") long familyStudentId,
                                                @Param("testIds") List<Long> testIds);
    List<TestFileEntity> findTestFiles(@Param("familyStudentId") long familyStudentId,
                                       @Param("testNo") String testNo);
    TestFileEntity findTestFile(@Param("familyStudentId") long familyStudentId, @Param("testNo") String testNo,
                                @Param("fileId") long fileId);
    int countTestFiles(@Param("familyStudentId") long familyStudentId, @Param("testNo") String testNo);
    int findMaxDisplayOrder(@Param("familyStudentId") long familyStudentId, @Param("testNo") String testNo);
    int insertTestFile(@Param("file") TestFileEntity file, @Param("operator") String operator);
    int updateTestFileMeta(@Param("familyStudentId") long familyStudentId, @Param("testNo") String testNo,
                           @Param("fileId") long fileId, @Param("displayOrder") int displayOrder,
                           @Param("comment") String comment, @Param("operator") String operator);
    int updateTestFileImage(@Param("familyStudentId") long familyStudentId, @Param("testNo") String testNo,
                            @Param("fileId") long fileId, @Param("storedFileName") String storedFileName,
                            @Param("extension") String extension, @Param("mimeType") String mimeType,
                            @Param("fileSize") Long fileSize, @Param("sha256") String sha256,
                            @Param("path") String path, @Param("thumbnail500") String thumbnail500,
                            @Param("thumbnail200") String thumbnail200, @Param("thumbnail50") String thumbnail50,
                            @Param("operator") String operator);
    int deleteTestFile(@Param("familyStudentId") long familyStudentId, @Param("testNo") String testNo,
                       @Param("fileId") long fileId);
    int deleteTestFilesByTestId(@Param("testId") long testId);
}
