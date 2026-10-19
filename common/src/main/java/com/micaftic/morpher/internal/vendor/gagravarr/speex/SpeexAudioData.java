/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.micaftic.morpher.internal.vendor.gagravarr.speex;

import com.micaftic.morpher.internal.vendor.gagravarr.ogg.OggPacket;
import com.micaftic.morpher.internal.vendor.gagravarr.ogg.OggStreamAudioData;

/**
 * Raw, compressed audio data
 */
public class SpeexAudioData extends OggStreamAudioData implements SpeexPacket {
    public SpeexAudioData(OggPacket pkt) {
        super(pkt);
    }
    public SpeexAudioData(byte[] data) {
        super(data);
    }
}
