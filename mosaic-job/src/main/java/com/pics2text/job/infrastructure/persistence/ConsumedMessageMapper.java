package com.pics2text.job.infrastructure.persistence;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.Instant;
import java.util.UUID;

@Mapper
public interface ConsumedMessageMapper {
    @Select("""
            SELECT EXISTS(SELECT 1 FROM consumed_message
              WHERE consumer_name = #{consumerName} AND message_id = #{messageId})
            """)
    boolean exists(@Param("consumerName") String consumerName, @Param("messageId") UUID messageId);

    @Insert("""
            INSERT INTO consumed_message(consumer_name, message_id, job_id, consumed_at)
            VALUES(#{consumerName}, #{messageId}, #{jobId}, #{now})
            ON CONFLICT DO NOTHING
            """)
    int insert(@Param("consumerName") String consumerName, @Param("messageId") UUID messageId,
            @Param("jobId") UUID jobId, @Param("now") Instant now);
}
