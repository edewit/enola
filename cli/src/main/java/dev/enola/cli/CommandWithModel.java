/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * Copyright 2023-2024 The Enola <https://enola.dev> Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package dev.enola.cli;

import dev.enola.common.io.resource.ResourceProviders;
import dev.enola.core.EnolaServiceProvider;
import dev.enola.core.Repository;
import dev.enola.core.grpc.EnolaGrpcClientProvider;
import dev.enola.core.grpc.EnolaGrpcInProcess;
import dev.enola.core.grpc.ServiceProvider;
import dev.enola.core.meta.EntityKindRepository;
import dev.enola.core.meta.proto.Type;
import dev.enola.core.proto.EnolaServiceGrpc.EnolaServiceBlockingStub;
import dev.enola.core.type.TypeRepositoryBuilder;

import picocli.CommandLine.ArgGroup;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

import java.net.URI;

public abstract class CommandWithModel implements CheckedRunnable {

    protected EnolaServiceProvider esp;

    @Spec CommandSpec spec;

    @ArgGroup(multiplicity = "1")
    ModelOrServer group;

    private EnolaServiceBlockingStub gRPCService;

    @Override
    public final void run() throws Exception {
        // TODO Fix design; as-is, this may stay null if --server instead of --model is used
        EntityKindRepository ekr = null;

        // TODO Move elsewhere for continuous ("shell") mode, as this is "expensive".
        ServiceProvider grpc = null;
        if (group.model != null) {
            var modelResource = new ResourceProviders().getReadableResource(group.model);
            ekr = new EntityKindRepository();
            ekr.load(modelResource);
            Repository<Type> tyr = new TypeRepositoryBuilder().build();
            // TODO --types for Types (and more?), e.g. from MD, YAML, textproto, etc.
            esp = new EnolaServiceProvider(ekr, tyr);
            var enolaService = esp.getEnolaService();
            grpc = new EnolaGrpcInProcess(esp, enolaService, false); // direct, single-threaded!
            gRPCService = grpc.get();
        } else if (group.server != null) {
            grpc = new EnolaGrpcClientProvider(group.server, false); // direct, single-threaded!
            gRPCService = grpc.get();
        }

        try {
            run(ekr, gRPCService);
        } finally {
            grpc.close();
        }
    }

    // TODO Pass only the EnolaServiceBlockingStub through, remove the EntityKindRepository from
    // here
    protected abstract void run(EntityKindRepository ekr, EnolaServiceBlockingStub service)
            throws Exception;

    static class ModelOrServer {

        @Option(
                names = {"--model", "-m"},
                required = true,
                description = "URI to EntityKinds (e.g. file:model.yaml)")
        private URI model;

        @Option(
                names = {"--server", "-s"},
                required = true,
                description = "Target of an Enola gRPC Server (e.g. localhost:7070)")
        private String server;
    }
}
