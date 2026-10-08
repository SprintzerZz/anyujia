package com.anyujia.anyujiaadminservice.config.service;

import java.util.List;
import java.util.Map;

import com.anyujia.anyujiaadminapi.config.domain.dto.DictionaryDataAddReqDTO;
import com.anyujia.anyujiaadminapi.config.domain.dto.DictionaryDataDTO;
import com.anyujia.anyujiaadminapi.config.domain.dto.DictionaryDataEditReqDTO;
import com.anyujia.anyujiaadminapi.config.domain.dto.DictionaryDataListReqDTO;
import com.anyujia.anyujiaadminapi.config.domain.dto.DictionaryTypeListReqDTO;
import com.anyujia.anyujiaadminapi.config.domain.dto.DictionaryTypeWriteReqDTO;
import com.anyujia.anyujiaadminapi.config.domain.vo.DictionaryDataVO;
import com.anyujia.anyujiaadminapi.config.domain.vo.DictionaryTypeVO;
import com.anyujia.anyujiacommondomain.domain.vo.BasePageVO;

/**
 * 字典服务接口。
 */
public interface ISysDictionaryService {

    /**
     * 新增字典类型。
     *
     * @param dictionaryTypeWriteReqDTO 字典类型写操作请求
     * @return 字典类型主键 ID
     */
    Long addType(DictionaryTypeWriteReqDTO dictionaryTypeWriteReqDTO);

    /**
     * 分页查询字典类型列表。
     *
     * @param dictionaryTypeListReqDTO 字典类型分页查询请求
     * @return 字典类型分页结果
     */
    BasePageVO<DictionaryTypeVO> listType(DictionaryTypeListReqDTO dictionaryTypeListReqDTO);

    /**
     * 编辑字典类型。
     *
     * @param dictionaryTypeWriteReqDTO 字典类型写操作请求
     * @return 字典类型主键 ID
     */
    Long editType(DictionaryTypeWriteReqDTO dictionaryTypeWriteReqDTO);

    /**
     * 新增字典数据。
     *
     * @param dictionaryDataAddReqDTO 字典数据新增请求
     * @return 字典数据主键 ID
     */
    Long addData(DictionaryDataAddReqDTO dictionaryDataAddReqDTO);

    /**
     * 分页查询字典数据列表。
     *
     * @param dictionaryDataListReqDTO 字典数据分页查询请求
     * @return 字典数据分页结果
     */
    BasePageVO<DictionaryDataVO> listData(DictionaryDataListReqDTO dictionaryDataListReqDTO);

    /**
     * 编辑字典数据。
     *
     * @param dictionaryDataEditReqDTO 字典数据编辑请求
     * @return 字典数据主键 ID
     */
    Long editData(DictionaryDataEditReqDTO dictionaryDataEditReqDTO);

    /**
     * 根据字典类型键查询该类型下的所有字典数据。
     *
     * @param typeKey 字典类型键
     * @return 字典数据列表
     */
    List<DictionaryDataDTO> selectDictDataByType(String typeKey);

    /**
     * 根据多个字典类型键查询对应字典数据。
     *
     * @param typeKeys 字典类型键列表
     * @return 字典类型键到字典数据列表的映射
     */
    Map<String, List<DictionaryDataDTO>> selectDictDataByTypes(List<String> typeKeys);

    /**
     * 根据字典数据业务键查询字典数据。
     *
     * @param dataKey 字典数据业务键
     * @return 字典数据
     */
    DictionaryDataDTO getDictDataByKey(String dataKey);

    /**
     * 根据多个字典数据业务键查询字典数据。
     *
     * @param dataKeys 字典数据业务键列表
     * @return 字典数据列表
     */
    List<DictionaryDataDTO> getDictDataByKeys(List<String> dataKeys);
}
