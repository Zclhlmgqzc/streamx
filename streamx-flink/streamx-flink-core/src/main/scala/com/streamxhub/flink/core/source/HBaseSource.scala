/**
 * Copyright (c) 2019 The StreamX Project
 * <p>
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package com.streamxhub.flink.core.source

import java.util.Properties

import com.streamxhub.common.util.Logger
import com.streamxhub.flink.core.StreamingContext
import com.streamxhub.flink.core.wrapper.HBaseQuery
import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.configuration.Configuration
import org.apache.hadoop.hbase.client._
import org.apache.flink.streaming.api.functions.source.RichSourceFunction
import org.apache.flink.streaming.api.functions.source.SourceFunction.SourceContext
import org.apache.flink.streaming.api.scala.DataStream

import scala.collection.JavaConversions._
import scala.annotation.meta.param
import scala.collection.immutable.Map

object HBaseSource {

  def apply(@(transient@param) ctx: StreamingContext, overrideParams: Map[String, String] = Map.empty[String, String]): HBaseSource = new HBaseSource(ctx, overrideParams)

}

/*
 * @param ctx
 * @param overrideParams
 */
class HBaseSource(@(transient@param) val ctx: StreamingContext, overrideParams: Map[String, String] = Map.empty[String, String]) {

  def getDataStream[R: TypeInformation](query: () => HBaseQuery, func: Result => R)(implicit config: Properties): DataStream[R] = {
    overrideParams.foreach(x => config.setProperty(x._1, x._2))
    val hBaseFunc = new HBaseSourceFunction[R](query, func)
    ctx.addSource(hBaseFunc)
  }

}


class HBaseSourceFunction[R: TypeInformation](queryFunc: () => HBaseQuery, func: Result => R)(implicit prop: Properties) extends RichSourceFunction[R] with Logger {

  private[this] var isRunning = true

  private[this] var table: Table = _

  @throws[Exception]
  override def open(parameters: Configuration): Unit = {
    super.open(parameters)
  }

  override def run(ctx: SourceContext[R]): Unit = {
    while (isRunning) {
      val query = queryFunc()
      require(query != null && query.getTable != null, "[StreamX] HBaseSource query and query's param table muse be not null ")
      table = query.getTable(prop)
      table.getScanner(query).foreach(x => ctx.collect(func(x)))
    }
  }

  override def cancel(): Unit = this.isRunning = false

  override def close(): Unit = {
    super.close()
    if (table != null) {
      table.close()
    }
  }
}
