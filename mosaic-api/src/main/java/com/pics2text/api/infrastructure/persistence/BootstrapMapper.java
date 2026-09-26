package com.pics2text.api.infrastructure.persistence;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface BootstrapMapper {

    @Update("""
            UPDATE platform_metadata
            SET metadata_value = 'complete', updated_at = CURRENT_TIMESTAMP
            WHERE metadata_key = 'identity_bootstrap' AND metadata_value = 'pending'
            """)
    int claim();
}
