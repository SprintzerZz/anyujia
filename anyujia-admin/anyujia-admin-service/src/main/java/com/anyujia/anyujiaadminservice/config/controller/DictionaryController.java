package com.anyujia.anyujiaadminservice.config.controller;

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
import com.anyujia.anyujiaadminapi.config.feign.DictionaryFeignClient;
import com.anyujia.anyujiaadminservice.config.service.ISysDictionaryService;
import com.anyujia.anyujiacommondomain.domain.ApiResult;
import com.anyujia.anyujiacommondomain.domain.vo.BasePageVO;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 字典服务相关接口。
 *
 * <p>实现 {@link DictionaryFeignClient}，保持 Controller 暴露路径与 Feign 远程调用路径一致。</p>
 */
@RestController
@RequiredArgsConstructor
public class DictionaryController implements DictionaryFeignClient {

    /**
     * 字典服务。
     */
    private final ISysDictionaryService sysDictionaryService;

    /**
     * 新增字典类型。
     *
     * @param dictionaryTypeWriteReqDTO 字典类型新增请求
     * @return 字典类型主键 ID
     */
    @Override
    public ApiResult<Long> addType(@RequestBody @Validated DictionaryTypeWriteReqDTO dictionaryTypeWriteReqDTO) {
        return ApiResult.success(sysDictionaryService.addType(dictionaryTypeWriteReqDTO));
    }

    /**
     * 分页查询字典类型列表。
     *
     * @param dictionaryTypeListReqDTO 字典类型分页查询请求
     * @return 字典类型分页结果
     */
    @Override
    public ApiResult<BasePageVO<DictionaryTypeVO>> listType(@Validated DictionaryTypeListReqDTO dictionaryTypeListReqDTO) {
        return ApiResult.success(sysDictionaryService.listType(dictionaryTypeListReqDTO));
    }

    /**
     * 编辑字典类型。
     *
     * @param dictionaryTypeWriteReqDTO 字典类型编辑请求
     * @return 字典类型主键 ID
     */
    @Override
    public ApiResult<Long> editType(@RequestBody @Validated DictionaryTypeWriteReqDTO dictionaryTypeWriteReqDTO) {
        return ApiResult.success(sysDictionaryService.editType(dictionaryTypeWriteReqDTO));
    }

    /**
     * 新增字典数据。
     *
     * @param dictionaryDataAddReqDTO 字典数据新增请求
     * @return 字典数据主键 ID
     */
    @Override
    public ApiResult<Long> addData(@RequestBody @Validated DictionaryDataAddReqDTO dictionaryDataAddReqDTO) {
        return ApiResult.success(sysDictionaryService.addData(dictionaryDataAddReqDTO));
    }

    /**
     * 分页查询字典数据列表。
     *
     * @param dictionaryDataListReqDTO 字典数据分页查询请求
     * @return 字典数据分页结果
     */
    @Override
    public ApiResult<BasePageVO<DictionaryDataVO>> listData(@Validated DictionaryDataListReqDTO dictionaryDataListReqDTO) {
        return ApiResult.success(sysDictionaryService.listData(dictionaryDataListReqDTO));
    }

    /**
     * 编辑字典数据。
     *
     * @param dictionaryDataEditReqDTO 字典数据编辑请求
     * @return 字典数据主键 ID
     */
    @Override
    public ApiResult<Long> editData(@RequestBody @Validated DictionaryDataEditReqDTO dictionaryDataEditReqDTO) {
        return ApiResult.success(sysDictionaryService.editData(dictionaryDataEditReqDTO));
    }

    /**
     * 根据字典类型键查询启用状态的字典数据。
     *
     * @param typeKey 字典类型键
     * @return 字典数据列表
     */
    @Override
    public ApiResult<List<DictionaryDataDTO>> selectDictDataByType(String typeKey) {
        return ApiResult.success(sysDictionaryService.selectDictDataByType(typeKey));
    }

    /**
     * 根据多个字典类型键查询启用状态的字典数据，并按类型键分组。
     *
     * @param typeKeys 字典类型键列表
     * @return 字典类型键到字典数据列表的映射
     */
    @Override
    public ApiResult<Map<String, List<DictionaryDataDTO>>> selectDictDataByTypes(List<String> typeKeys) {
        return ApiResult.success(sysDictionaryService.selectDictDataByTypes(typeKeys));
    }

    /**
     * 根据字典数据业务键查询启用状态的字典数据。
     *
     * @param dataKey 字典数据业务键
     * @return 字典数据
     */
    @Override
    public ApiResult<DictionaryDataDTO> getDictDataByKey(String dataKey) {
        return ApiResult.success(sysDictionaryService.getDictDataByKey(dataKey));
    }

    /**
     * 根据多个字典数据业务键查询启用状态的字典数据。
     *
     * @param dataKeys 字典数据业务键列表
     * @return 字典数据列表
     */
    @Override
    public ApiResult<List<DictionaryDataDTO>> getDictDataByKeys(List<String> dataKeys) {
        return ApiResult.success(sysDictionaryService.getDictDataByKeys(dataKeys));
    }
}
