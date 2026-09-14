// Original synthesized effects. No recordings, samples or third-party assets. CC0.
import {mkdirSync,writeFileSync} from 'node:fs';
const rate=22050;
let seed=3207;
const noise=()=>{seed=(Math.imul(seed,1664525)+1013904223)>>>0;return seed/2147483648-1};
function wave(name,duration,signal) {
 const count=Math.floor(rate*duration),data=Buffer.alloc(44+count*2);
 data.write('RIFF');data.writeUInt32LE(36+count*2,4);data.write('WAVEfmt ',8);data.writeUInt32LE(16,16);
 data.writeUInt16LE(1,20);data.writeUInt16LE(1,22);data.writeUInt32LE(rate,24);data.writeUInt32LE(rate*2,28);
 data.writeUInt16LE(2,32);data.writeUInt16LE(16,34);data.write('data',36);data.writeUInt32LE(count*2,40);
 for(let i=0;i<count;i++) data.writeInt16LE(Math.round(Math.max(-1,Math.min(1,signal(i/rate,duration)))*32767),44+i*2);
 writeFileSync(`app/src/main/res/raw/${name}.wav`,data);
}
const launch=(t,d)=>Math.sin(Math.PI*t/d)**1.4*(.11*noise()+.09*Math.sin(2*Math.PI*(440*t+650*t*t)));
const burst=(t)=>Math.min(1,t/.015)*Math.exp(-7*t)*(.26*noise()+.12*Math.sin(2*Math.PI*130*t));
mkdirSync('app/src/main/res/raw',{recursive:true});
wave('flare_launch',.48,launch);wave('flare_burst',.65,burst);
wave('flare_notification',1.05,t=>t<.36?launch(t,.36):burst(t-.36));
