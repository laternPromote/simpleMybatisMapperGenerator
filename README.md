# simpleMybatisMapperGenerator
generate simple form mybatis mapper based on the datasource, also generate entity, dao, daoImpl and etc.

***

### 生成简单样式的mybatis mapper, 顺带生成实体类,dao,daoImp等等
* 运行Main.java中的main方法以生成代码
* 官方的mybatisGenerator生成的mapper较为复杂, 适合在逻辑层进行类似hibernate的写法
* 我这个生成的mapper较为简洁, 是单表的增删改查, 如下所示
* example
```xml
<?xml version="1.0" encoding="UTF-8" ?>
<!DOCTYPE mapper PUBLIC "-//mybatis.org//DTD Mapper 3.0//EN" "http://mybatis.org/dtd/mybatis-3-mapper.dtd">

<mapper namespace="IUserDao">
    <select id="queryUser" resultType="com.example.entity.monitor.User"
            parameterType="com.example.entity.monitor.User">
        SELECT * FROM user
        <where>
            <if test="userId !=null and userId!=''">and userId=#{userId}</if>
            <if test="userName !=null and userName!=''">and userName=#{userName}</if>
            <if test="password !=null and password!=''">and password=#{password}</if>
        </where>
    </select>

    <insert id="saveUser" parameterType="com.example.entity.monitor.User" useGeneratedKeys="true" keyProperty="userId">
INSERT INTO user (userId, userName, password) VALUE (#{userId}, #{userName}, #{password}) 
</insert>

    <delete id="deleteUser" parameterType="com.example.entity.monitor.User">
DELETE FROM user WHERE userId=#{userId}
</delete>

    <update id="updateUser" parameterType="com.example.entity.monitor.User">
        UPDATE user
        <set>
            <if test="userId!=null and userId!=''">userId=#{userId},</if>
            <if test="userName!=null and userName!=''">userName=#{userName},</if>
            <if test="password!=null and password!=''">password=#{password}</if>
        </set>
        where userId = #{userId}
    </update>

</mapper>
```

***

### A simple mybatis mapper generator
* besides generate mapper, it also generates entity, dao, daoImpl, service, serviceImpl
* It generates a simple form of mybatis mapper
* Mybatis has an offical generator called MybatisGenerator, but the mapper file it generates is too complicated
* And it encourages a hibernate style to write the code
* the mapper example is shown above
* run the main method in Main.java to generate code
