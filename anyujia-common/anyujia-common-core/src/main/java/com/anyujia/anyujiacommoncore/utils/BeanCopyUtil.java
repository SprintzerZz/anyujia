package com.anyujia.anyujiacommoncore.utils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

import org.springframework.beans.BeanUtils;

/**
 * Bean 拷贝工具类。
 *
 * 该工具类基于 Spring {@code BeanUtils.copyProperties}，适合处理字段名一致、类型兼容的简单对象拷贝。
 *
 * 注意事项：
 * 1. 变量名不一样时不会自动拷贝，例如 source.userName 不能自动拷贝到 target.name。
 * 2. 数据类型不兼容时不会自动类型转换，例如 String 不能自动转 Integer，Long 不能自动转 Date。
 * 3. 只做浅拷贝，引用类型字段只复制引用地址，不会递归创建新对象。
 * 4. 嵌套对象、集合字段、枚举转换、字段重命名等复杂场景，建议手动转换或使用 MapStruct。
 */
public class BeanCopyUtil extends BeanUtils {

    private BeanCopyUtil() {
    }

    /**
     * 集合数据拷贝。
     *
     * Spring BeanUtils.copyProperties 默认只支持单个 Java Bean 拷贝，不支持直接拷贝 List。
     * 该方法会遍历源集合，为每条源数据创建一个目标对象，并把同名属性复制到目标对象中。
     *
     * 拷贝规则：
     * 1. 只拷贝源对象和目标对象中属性名相同的字段。
     * 2. 只拷贝类型兼容的字段，不负责 String、Integer、Date 等类型之间的转换。
     * 3. 对普通字段是值拷贝；对对象、集合等引用字段是浅拷贝。
     * 4. 如果目标对象字段名与源对象不同，需要在调用后手动赋值。
     *
     * 示例：
     * {@code List<UserVO> userVOList = BeanCopyUtil.copyListProperties(userList, UserVO::new);}
     *
     * @param sources 源数据集合
     * @param target 目标对象构造方法，例如 UserVO::new
     * @param <S> 源对象类型
     * @param <T> 目标对象类型
     * @return 拷贝后的目标对象集合；源集合为空时返回空集合
     */
    public static <S, T> List<T> copyListProperties(List<S> sources, Supplier<T> target) {
        if (sources == null || sources.isEmpty()) {
            return Collections.emptyList();
        }
        if (target == null) {
            throw new IllegalArgumentException("Target supplier must not be null");
        }

        List<T> list = new ArrayList<>(sources.size());
        for (S source : sources) {
            T t = target.get();
            copyProperties(source, t);
            list.add(t);
        }
        return list;
    }
}
