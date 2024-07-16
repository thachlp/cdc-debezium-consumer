package com.thachlp.cdc.consumer.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.thachlp.cdc.consumer.data.TableMetaDataAccess;
import com.thachlp.cdc.consumer.pojo.CDCObject;
import com.thachlp.cdc.consumer.pojo.DMLType;
import com.thachlp.cdc.consumer.querybuilder.QueryBuildable;
import com.thachlp.cdc.consumer.querybuilder.QueryBuilderDelete;
import com.thachlp.cdc.consumer.querybuilder.QueryBuilderInsert;
import com.thachlp.cdc.consumer.querybuilder.QueryBuilderUpdate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class DataChangeListener {

    private final ObjectMapper objectMapper;
    private static final Map<String, List<String>> mapPrivateKey = new HashMap<>();
    private final TableMetaDataAccess tableMetaDataAccess;
    private static final EnumMap<DMLType, QueryBuildable> actionBuilder = new EnumMap<>(DMLType.class);

    static {
        actionBuilder.put(DMLType.INSERT, new QueryBuilderInsert());
        actionBuilder.put(DMLType.UPDATE, new QueryBuilderUpdate());
        actionBuilder.put(DMLType.DELETE, new QueryBuilderDelete());
    }

    @KafkaListener(topicPattern = "#{@topicPattern}", groupId = "coffee_shop")
    public void listen(String message) {
        final CDCObject jsonNode;
        try {
            jsonNode = objectMapper.readValue(message, CDCObject.class);
            final String database = jsonNode.getPayload().getSource().getDb();
            final String table = jsonNode.getPayload().getSource().getTable();

            if (!mapPrivateKey.containsKey(table)) {
                mapPrivateKey.put(table, tableMetaDataAccess.getPrimaryKeysFrom(database, table));
            }

            final String query = actionBuilder.get(getActionType(jsonNode.getPayload()))
                    .build(table, mapPrivateKey.get(table), jsonNode);
            log.info("Executed query: {}",  query);
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }
    }

    private static DMLType getActionType(CDCObject.CDCPayload payload) {
        if (payload.getBefore() == null) {
            return DMLType.INSERT;
        }
        if (payload.getAfter() == null) {
            return DMLType.DELETE;
        }
        return DMLType.UPDATE;
    }
}