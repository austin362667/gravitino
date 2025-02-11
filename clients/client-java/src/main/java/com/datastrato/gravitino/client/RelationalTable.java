/*
 * Copyright 2024 Datastrato Pvt Ltd.
 * This software is licensed under the Apache License version 2.
 */
package com.datastrato.gravitino.client;

import static com.datastrato.gravitino.dto.util.DTOConverters.toDTO;

import com.datastrato.gravitino.Audit;
import com.datastrato.gravitino.Namespace;
import com.datastrato.gravitino.dto.rel.TableDTO;
import com.datastrato.gravitino.dto.rel.partitions.PartitionDTO;
import com.datastrato.gravitino.dto.requests.AddPartitionsRequest;
import com.datastrato.gravitino.dto.responses.PartitionListResponse;
import com.datastrato.gravitino.dto.responses.PartitionNameListResponse;
import com.datastrato.gravitino.dto.responses.PartitionResponse;
import com.datastrato.gravitino.exceptions.NoSuchPartitionException;
import com.datastrato.gravitino.exceptions.PartitionAlreadyExistsException;
import com.datastrato.gravitino.rel.Column;
import com.datastrato.gravitino.rel.SupportsPartitions;
import com.datastrato.gravitino.rel.Table;
import com.datastrato.gravitino.rel.expressions.distributions.Distribution;
import com.datastrato.gravitino.rel.expressions.sorts.SortOrder;
import com.datastrato.gravitino.rel.expressions.transforms.Transform;
import com.datastrato.gravitino.rel.indexes.Index;
import com.datastrato.gravitino.rel.partitions.Partition;
import com.google.common.annotations.VisibleForTesting;
import java.net.URLEncoder;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import javax.annotation.Nullable;
import lombok.SneakyThrows;

public class RelationalTable implements Table, SupportsPartitions {

  public static RelationalTable from(
      Namespace namespace, TableDTO tableDTO, RESTClient restClient) {
    return new RelationalTable(namespace, tableDTO, restClient);
  }

  private final TableDTO tableDTO;
  private final RESTClient restClient;
  private final Namespace namespace;

  public RelationalTable(Namespace namespace, TableDTO tableDTO, RESTClient restClient) {
    this.namespace = namespace;
    this.tableDTO = tableDTO;
    this.restClient = restClient;
  }

  public Namespace namespace() {
    return namespace;
  }

  @Override
  public String name() {
    return tableDTO.name();
  }

  @Override
  public Column[] columns() {
    return tableDTO.columns();
  }

  @Override
  public Transform[] partitioning() {
    return tableDTO.partitioning();
  }

  @Override
  public SortOrder[] sortOrder() {
    return tableDTO.sortOrder();
  }

  @Override
  public Distribution distribution() {
    return tableDTO.distribution();
  }

  @Nullable
  @Override
  public String comment() {
    return tableDTO.comment();
  }

  @Override
  public Map<String, String> properties() {
    return tableDTO.properties();
  }

  @Override
  public Audit auditInfo() {
    return tableDTO.auditInfo();
  }

  @Override
  public Index[] index() {
    return tableDTO.index();
  }

  @Override
  public String[] listPartitionNames() {
    PartitionNameListResponse resp =
        restClient.get(
            getPartitionRequestPath(),
            PartitionNameListResponse.class,
            Collections.emptyMap(),
            ErrorHandlers.partitionErrorHandler());
    return resp.partitionNames();
  }

  @VisibleForTesting
  public String getPartitionRequestPath() {
    return "api/metalakes/"
        + namespace.level(0)
        + "/catalogs/"
        + namespace.level(1)
        + "/schemas/"
        + namespace.level(2)
        + "/tables/"
        + name()
        + "/partitions";
  }

  @Override
  public Partition[] listPartitions() {
    Map<String, String> params = new HashMap<>();
    params.put("details", "true");
    PartitionListResponse resp =
        restClient.get(
            getPartitionRequestPath(),
            params,
            PartitionListResponse.class,
            Collections.emptyMap(),
            ErrorHandlers.partitionErrorHandler());
    return resp.getPartitions();
  }

  @Override
  public Partition getPartition(String partitionName) throws NoSuchPartitionException {
    PartitionResponse resp =
        restClient.get(
            formatPartitionRequestPath(getPartitionRequestPath(), partitionName),
            PartitionResponse.class,
            Collections.emptyMap(),
            ErrorHandlers.partitionErrorHandler());
    return resp.getPartition();
  }

  @Override
  public Partition addPartition(Partition partition) throws PartitionAlreadyExistsException {
    AddPartitionsRequest req = new AddPartitionsRequest(new PartitionDTO[] {toDTO(partition)});
    req.validate();

    PartitionListResponse resp =
        restClient.post(
            getPartitionRequestPath(),
            req,
            PartitionListResponse.class,
            Collections.emptyMap(),
            ErrorHandlers.partitionErrorHandler());
    resp.validate();

    return resp.getPartitions()[0];
  }

  @Override
  public boolean dropPartition(String partitionName) {
    throw new UnsupportedOperationException();
  }

  @Override
  public SupportsPartitions supportPartitions() throws UnsupportedOperationException {
    return this;
  }

  @VisibleForTesting
  @SneakyThrows // Encode charset is fixed to UTF-8, so this is safe.
  protected static String formatPartitionRequestPath(String prefix, String partitionName) {
    return prefix + "/" + URLEncoder.encode(partitionName, "UTF-8");
  }
}
