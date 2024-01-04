/*
 * Copyright (C) 2020-2024 Hedera Hashgraph, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.hedera.services.bdd.suites.utils.sysfiles;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.hederahashgraph.api.proto.java.FeeData;
import com.hederahashgraph.api.proto.java.TransactionFeeSchedule;
import java.util.ArrayList;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class ScheduleEntryPojo {
    String hederaFunctionality;
    List<ScopedResourcePricesPojo> fees;

    public static ScheduleEntryPojo from(TransactionFeeSchedule grpc) {
        var pojo = new ScheduleEntryPojo();

        pojo.setHederaFunctionality(grpc.getHederaFunctionality().toString());

        List<ScopedResourcePricesPojo> feesList = new ArrayList<>();

        for (FeeData feeData : grpc.getFeesList()) {
            var subType = feeData.getSubType();
            var nodePrices = ResourcePricesPojo.from(feeData.getNodedata());
            var servicePrices = ResourcePricesPojo.from(feeData.getServicedata());
            var networkPrices = ResourcePricesPojo.from(feeData.getNetworkdata());

            var scopedPrices = new ScopedResourcePricesPojo();
            scopedPrices.setNodedata(nodePrices);
            scopedPrices.setNetworkdata(networkPrices);
            scopedPrices.setServicedata(servicePrices);
            scopedPrices.setSubType(subType);

            feesList.add(scopedPrices);
        }

        pojo.setFees(feesList);
        return pojo;
    }

    public String getHederaFunctionality() {
        return hederaFunctionality;
    }

    public void setHederaFunctionality(String hederaFunctionality) {
        this.hederaFunctionality = hederaFunctionality;
    }

    public List<ScopedResourcePricesPojo> getFees() {
        return fees;
    }

    public void setFees(List<ScopedResourcePricesPojo> feeData) {
        this.fees = feeData;
    }
}
