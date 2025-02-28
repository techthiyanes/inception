/*
 * ## INCEpTION ##
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
 *
 * ## brat ##
 * Copyright (C) 2010-2012 The brat contributors, all rights reserved.
 * 
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to
 * use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies
 * of the Software, and to permit persons to whom the Software is furnished to do
 * so, subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
import { SVGTypeMapping } from "@svgdotjs/svg.js";
import { Svg } from "@svgdotjs/svg.js";
import { Fragment } from "./Fragment";
import { Row } from "./Row";

/**
 * Chunk of text generated from the token offsets and representing one or more tokens.
 */
export class Chunk {
  index: number = undefined;
  text: string = undefined;
  from: number = undefined;
  to: number = undefined;
  space: string = undefined;
  fragments: Fragment[] = [];
  lastSpace = undefined;
  nextSpace = undefined;
  sentence: number = undefined;
  group: SVGTypeMapping<SVGGElement> = undefined;
  highlightGroup: SVGTypeMapping<SVGGElement> = undefined;
  // chunk.markedTextStart.push([textNo, true, from - chunk.from, null, markedType]);
  markedTextStart: Array<unknown> = undefined;
  // chunk.markedTextEnd.push([textNo, false, to - chunk.from]);
  markedTextEnd: Array<unknown> = undefined;
  right = undefined;
  row: Row = undefined;
  textX = undefined;
  translation: { x: number, y: number } = {x: 0, y: 0};
  firstFragmentIndex = undefined;
  lastFragmentIndex = undefined;
  rtlsizes: { charDirection: Array<"rtl" | "ltr">, charAttrs: Array<{order: number, width: number, direction: "rtl" | "ltr"}>, corrFactor: number } = undefined;

  constructor(index: number, text: string, from: number, to: number, space: string) {
    this.index = index;
    this.text = text;
    this.from = from;
    this.to = to;
    this.space = space;
    Object.seal(this);
  }

  renderText(svg: Svg, rowTextGroup: SVGTypeMapping<SVGGElement> ) {
    svg.plain(this.text)
      .attr({
        // Storing the exact position in the attributs here is an optimization because that
        // allows us to obtain the position direcly later without the browser having to actually
        // layout stuff
        x: this.textX,
        y: this.row.textY,
        'data-chunk-id': this.index
      })
      .addTo(rowTextGroup);
  }
}
