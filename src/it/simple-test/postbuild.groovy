/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
File buildLog = new File( basedir, 'build.log' )
assert buildLog.text.contains( 'Container summerwind/h2spec' )
File surefireFile = new File(basedir, "target/surefire-reports/TEST-h2spec.xml")
assert surefireFile.exists()
def report = new XmlSlurper().parse(surefireFile)
// <testsuite name="3.5. HTTP/2 Connection Preface" >
def failingNode = report.'*'.find { node ->
  node.name() == 'testsuite' && node.@name == '3.5. HTTP/2 Connection Preface'
}

assert failingNode.@errors == '1'
assert failingNode.@package == 'http2/3.5'
assert failingNode.@name == '3.5. HTTP/2 Connection Preface'
