/*
 * Licensed to the Technische Universität Darmstadt under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The Technische Universität Darmstadt 
 * licenses this file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.
 *  
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package de.tudarmstadt.ukp.inception.websocket.config;

import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;

import de.tudarmstadt.ukp.inception.log.config.EventLoggingAutoConfiguration;
import de.tudarmstadt.ukp.inception.websocket.controller.LoggedEventMessageController;
import de.tudarmstadt.ukp.inception.websocket.footer.LoggedEventFooterItem;

@Configuration
@EnableWebSocketMessageBroker
@AutoConfigureAfter(EventLoggingAutoConfiguration.class)
@ConditionalOnProperty(prefix = "websocket", name = "enabled", havingValue = "true", matchIfMissing = true)
public class WebsocketAutoConfiguration
{
    @ConditionalOnBean(LoggedEventMessageController.class)
    @ConditionalOnProperty(name = "websocket.loggedevent.enabled", havingValue = "true")
    @Bean
    public LoggedEventFooterItem loggedEventFooterItem()
    {
        return new LoggedEventFooterItem();
    }

    @Bean
    public WebsocketConfig websocketConfig()
    {
        return new WebsocketConfig();
    }
}
