package com.study21.user.document;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface DocumentMapper {
    List<Long> findFamilyStudentIds(@Param("accountId") long accountId, @Param("accountType") String accountType);
    List<DocumentFolderEntity> findFolders(@Param("familyStudentId") long familyStudentId);
    DocumentFolderEntity findFolder(@Param("familyStudentId") long familyStudentId, @Param("folderId") long folderId);
    List<String> findFolderPathNames(@Param("familyStudentId") long familyStudentId, @Param("folderId") long folderId);
    int findFolderDepth(@Param("familyStudentId") long familyStudentId, @Param("folderId") long folderId);
    int findFolderSubtreeHeight(@Param("familyStudentId") long familyStudentId, @Param("folderId") long folderId);
    int insertFolder(DocumentFolderEntity folder);
    int updateFolder(@Param("familyStudentId") long familyStudentId, @Param("folderId") long folderId,
                     @Param("parentFolderId") Long parentFolderId,
                     @Param("folderName") String folderName, @Param("displayOrder") int displayOrder,
                     @Param("note") String note, @Param("operator") String operator);
    int countFolderInSubtree(@Param("familyStudentId") long familyStudentId,
                             @Param("rootFolderId") long rootFolderId, @Param("candidateFolderId") long candidateFolderId);
    int syncDocumentCategories(@Param("familyStudentId") long familyStudentId, @Param("folderId") long folderId);
    int countFolderChildren(@Param("familyStudentId") long familyStudentId, @Param("folderId") long folderId);
    int countFolderDocuments(@Param("familyStudentId") long familyStudentId, @Param("folderId") long folderId);
    int deleteFolder(@Param("familyStudentId") long familyStudentId, @Param("folderId") long folderId);
    List<DocumentEntity> findDocuments(@Param("familyStudentId") long familyStudentId,
                                       @Param("folderId") Long folderId, @Param("keyword") String keyword);
    DocumentEntity findDocument(@Param("familyStudentId") long familyStudentId, @Param("documentNo") String documentNo);
    int countDocumentNo(@Param("documentNo") String documentNo);
    int lockDocument(@Param("familyStudentId") long familyStudentId, @Param("documentNo") String documentNo);
    int insertDocument(DocumentEntity document);
    int updateDocument(@Param("familyStudentId") long familyStudentId, @Param("document") DocumentEntity document,
                       @Param("operator") String operator);
    int deleteDocument(@Param("familyStudentId") long familyStudentId, @Param("documentNo") String documentNo);
    List<DocumentFileEntity> findFiles(@Param("familyStudentId") long familyStudentId,
                                       @Param("documentNo") String documentNo);
    List<DocumentFileEntity> findAllFiles(@Param("familyStudentId") long familyStudentId);
    DocumentFileEntity findFile(@Param("familyStudentId") long familyStudentId,
                                @Param("documentNo") String documentNo, @Param("branchNo") int branchNo);
    int findMaxBranchNo(@Param("documentNo") String documentNo);
    int insertFile(@Param("file") DocumentFileEntity file, @Param("operator") String operator);
    int deleteFile(@Param("familyStudentId") long familyStudentId,
                   @Param("documentNo") String documentNo, @Param("branchNo") int branchNo);
    int deleteFilesByDocument(@Param("familyStudentId") long familyStudentId,
                              @Param("documentNo") String documentNo);
}
