package com.anyujia.anyujialegalservice.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("legal_document_block")
public class LegalDocumentBlock {
    @TableId(value = "id", type = IdType.ASSIGN_ID)
    private Long id;
    private Long contractId;
    private Integer blockIndex;
    private Integer pageNumber;
    private Integer paragraphIndex;
    private String locatorPrecision;
    private String heading;
    private String blockText;
    private Integer charStart;
    private Integer charEnd;
}
