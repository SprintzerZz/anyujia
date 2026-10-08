package com.anyujia.anyujiaadminapi.config.feign;

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
import com.anyujia.anyujiacommondomain.domain.ApiResult;
import com.anyujia.anyujiacommondomain.domain.vo.BasePageVO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.cloud.openfeign.SpringQueryMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 字典服务 Feign 客户端。
 *
 * <p>用于其他微服务通过服务名调用基础管理服务中的字典类型和字典数据接口。</p>
 */
@FeignClient(contextId = "dictionaryFeignClient", value = "anyujia-admin")
public interface DictionaryFeignClient {

    /**
     * 新增字典类型。
     *
     * @param dictionaryTypeWriteReqDTO 字典类型新增请求
     * @return 字典类型主键 ID
     */
    @PostMapping("/dictionary_type/add")
    ApiResult<Long> addType(@RequestBody DictionaryTypeWriteReqDTO dictionaryTypeWriteReqDTO);

    /**
     * 分页查询字典类型列表。
     *
     * @param dictionaryTypeListReqDTO 字典类型分页查询请求
     * @return 字典类型分页结果
     */
    @GetMapping("/dictionary_type/list")
    ApiResult<BasePageVO<DictionaryTypeVO>> listType(@SpringQueryMap DictionaryTypeListReqDTO dictionaryTypeListReqDTO);

    /**
     * 编辑字典类型。
     *
     * @param dictionaryTypeWriteReqDTO 字典类型编辑请求
     * @return 字典类型主键 ID
     */
    @PostMapping("/dictionary_type/edit")
    ApiResult<Long> editType(@RequestBody DictionaryTypeWriteReqDTO dictionaryTypeWriteReqDTO);

    /**
     * 新增字典数据。
     *
     * @param dictionaryDataAddReqDTO 字典数据新增请求
     * @return 字典数据主键 ID
     */
    @PostMapping("/dictionary_data/add")
    ApiResult<Long> addData(@RequestBody DictionaryDataAddReqDTO dictionaryDataAddReqDTO);

    /**
     * 分页查询字典数据列表。
     *
     * @param dictionaryDataListReqDTO 字典数据分页查询请求
     * @return 字典数据分页结果
     */
    @GetMapping("/dictionary_data/list")
    ApiResult<BasePageVO<DictionaryDataVO>> listData(@SpringQueryMap DictionaryDataListReqDTO dictionaryDataListReqDTO);

    /**
     * 编辑字典数据。
     *
     * @param dictionaryDataEditReqDTO 字典数据编辑请求
     * @return 字典数据主键 ID
     */
    @PostMapping("/dictionary_data/edit")
    ApiResult<Long> editData(@RequestBody DictionaryDataEditReqDTO dictionaryDataEditReqDTO);

    /**
     * 根据字典类型键查询启用状态的字典数据。
     *
     * @param typeKey 字典类型键
     * @return 字典数据列表
     */
    @GetMapping("/dictionary_data/type")
    ApiResult<List<DictionaryDataDTO>> selectDictDataByType(@RequestParam("typeKey") String typeKey);

    /**
     * 根据多个字典类型键查询启用状态的字典数据，并按类型键分组。
     *
     * @param typeKeys 字典类型键列表
     * @return 字典类型键到字典数据列表的映射
     */
    @GetMapping("/dictionary_data/types")
    ApiResult<Map<String, List<DictionaryDataDTO>>> selectDictDataByTypes(@RequestParam("typeKeys") List<String> typeKeys);

    /**
     * 根据字典数据业务键查询启用状态的字典数据。
     *
     * @param dataKey 字典数据业务键
     * @return 字典数据
     */
    @GetMapping("/dictionary_data/key")
    ApiResult<DictionaryDataDTO> getDictDataByKey(@RequestParam("dataKey") String dataKey);

    /**
     * 根据多个字典数据业务键查询启用状态的字典数据。
     *
     * @param dataKeys 字典数据业务键列表
     * @return 字典数据列表
     */
    @GetMapping("/dictionary_data/keys")
    ApiResult<List<DictionaryDataDTO>> getDictDataByKeys(@RequestParam("dataKeys") List<String> dataKeys);
}
