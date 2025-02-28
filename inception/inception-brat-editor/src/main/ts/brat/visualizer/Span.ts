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

import { Arc } from "./Arc";
import { Fragment } from "./Fragment";
import { Offsets, OffsetsList } from "./SourceData";

export class Span {
  id: string = undefined;
  type: string = undefined;
  totalDist = 0;
  numArcs = 0;
  generalType: string = undefined;
  headFragment: Fragment = undefined;
  unsegmentedOffsets: Array<Offsets> = [];
  offsets: OffsetsList = [];
  segmentedOffsetsMap = {};
  hidden = false;
  clippedAtStart = false;
  clippedAtEnd = false;
  incoming: Arc[] = [];
  outgoing: Arc[] = [];
  attributes: Record<string, string> = {};
  attributeText: string[] = [];
  attributeCues = {};
  attributeCueFor = {};
  annotatorNotes = undefined;
  attributeMerge: Record<string, unknown> = {}; // for box, cross, etc. that are span-global
  fragments: Fragment[] = [];
  normalized: string;
  normalizations: Array<[string, string, string]> = [];
  wholeFrom = undefined;
  wholeTo = undefined;
  comment = undefined; // { type: undefined, text: undefined };
  drawCurly = false;
  labelText: string;
  refedIndexSum = undefined;
  color: string;
  shadowClass: string;
  floor: number;
  marked;
  avgDist;
  text: string;
  cue: string;
  hovertext: string;
  actionButtons: boolean;

  /**
   * @param {*} id
   */
  constructor(id, type: string, offsets: OffsetsList, generalType: string) {
    this.id = id;
    this.type = type;
    this.unsegmentedOffsets = offsets;
    this.generalType = generalType;
    // Object.seal(this);
    this.initContainers();
  }

  static compare(spans: Record<string, Span>, a: string, b: string) {
    const aSpan = spans[a];
    const bSpan = spans[b];
    const tmp = aSpan.headFragment.from + aSpan.headFragment.to - bSpan.headFragment.from - bSpan.headFragment.to;
    if (tmp) {
      return tmp < 0 ? -1 : 1;
    }
    return 0;
  }

  initContainers() {
    this.incoming = [];
    this.outgoing = [];
    this.attributes = {};
    this.attributeText = [];
    this.attributeCues = {};
    this.attributeCueFor = {};
    this.attributeMerge = {};
    this.fragments = [];
    this.normalizations = [];
  }

  /**
   * @param {string} text
   */
  splitMultilineOffsets(text) {
    this.segmentedOffsetsMap = {};

    for (let fi = 0, nfi = 0; fi < this.unsegmentedOffsets.length; fi++) {
      /** @type {number | any} */ let begin = this.unsegmentedOffsets[fi][0];
      const end = this.unsegmentedOffsets[fi][1];

      for (let ti = begin; ti < end; ti++) {
        const c = text.charAt(ti);
        if (c === '\n' || c === '\r') {
          if (begin !== null) {
            this.offsets.push([begin, ti]);
            this.segmentedOffsetsMap[nfi++] = fi;
            begin = null;
          }
        } else if (begin === null) {
          begin = ti;
        }
      }

      if (begin !== null) {
        this.offsets.push([begin, end]);
        this.segmentedOffsetsMap[nfi++] = fi;
      }
    }
  }

  /**
   * Create a partial copy of the span with a new ID.
   *
   * @param {String} id
   * @returns the copy.
   */
  copy(id) {
    const span = $.extend(new Span(id, undefined, this.unsegmentedOffsets.slice(), undefined), this); // clone
    // read-only; shallow copy is fine
    span.offsets = this.offsets;
    span.segmentedOffsetsMap = this.segmentedOffsetsMap;
    return span;
  }

  buildFragments() {
    $.each(this.offsets, (offsetsNo, offsets) => {
      const from = offsets[0];
      const to = offsets[1];
      const fragment = new Fragment(offsetsNo, this, from, to);
      this.fragments.push(fragment);
    });

    // ensure ascending order
    this.fragments.sort(Fragment.midpointComparator);
    this.wholeFrom = this.fragments[0].from;
    this.wholeTo = this.fragments[this.fragments.length - 1].to;
    this.headFragment = this.fragments[this.fragments.length - 1];
  }

  get fragmentOffsets(): OffsetsList {
    return this.fragments.map(f => [f.from, f.to])
  }
}
