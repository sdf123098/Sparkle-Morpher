package com.micaftic.morpher.peer;

import org.junit.jupiter.api.Test;
import java.io.*;
import java.nio.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class PeerResourcePreflightTest {
    @Test void decodeReservationsArePeerScopedAndRejectBeforeArrayAllocation() {
        assertEquals(0,PeerResourceBudget.DECODE.used());
        try(var scope=new PeerDecodeScope()) {
            PeerDecodeScope.array(64*1024*1024,64*1024*1024);assertEquals(64L*1024*1024,PeerResourceBudget.DECODE.used());
            assertThrows(IllegalArgumentException.class,()->PeerDecodeScope.allocate(PeerResourceBudget.DECODE.capacity()-64L*1024*1024+1));
        }
        assertEquals(0,PeerResourceBudget.DECODE.used());PeerDecodeScope.allocate(Long.MAX_VALUE);assertEquals(0,PeerResourceBudget.DECODE.used());
    }
    @Test void oggDurationAndChannelsAreInspectedBeforePcmAllocation()throws Exception {
        assertEquals(48000L*2*2,PeerAudioPreflight.pcmBytes(ogg(2,48000)));
        assertThrows(IOException.class,()->PeerAudioPreflight.pcmBytes(ogg(8,48000)));
        assertThrows(IOException.class,()->PeerAudioPreflight.pcmBytes(ogg(2,48000L*301)));
    }
    private static byte[] ogg(int channels,long samples) {
        byte[] bytes=new byte[27+1+19];var buffer=ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        buffer.put("OggS".getBytes(StandardCharsets.US_ASCII)).put((byte)0).put((byte)6).putLong(samples).putInt(7).putInt(0).putInt(0).put((byte)1).put((byte)19).put("OpusHead".getBytes(StandardCharsets.US_ASCII)).put((byte)1).put((byte)channels);return bytes;
    }
    @Test void peerEmptyLoopsHaveAFuelLimitAndOtherDomainsKeepTheirOriginalPolicy() {
        class Domain implements PeerEvaluationDomain {long frame=1;public boolean peerInput(){return true;}public long evaluationFrame(){return frame;}}
        var domain=new Domain();var evaluator=new com.micaftic.morpher.molang.runtime.ExpressionEvaluatorImpl<>(domain);
        var body=new com.micaftic.morpher.molang.parser.ast.ExecutionScopeExpression(List.of());
        assertThrows(IllegalStateException.class,()->evaluator.loopFunciton(body,Integer.MAX_VALUE));
        domain.frame=2;assertEquals(1f,evaluator.evalAsFloat(new com.micaftic.morpher.molang.parser.ast.FloatExpression(1f)));
        new com.micaftic.morpher.molang.runtime.ExpressionEvaluatorImpl<>(new Object()).loopFunciton(body,200000);
    }
    @Test void maliciousCompilerNestingIsRejectedInsidePeerImportOnly()throws Exception {
        String expression="(".repeat(40)+"1"+")".repeat(40);
        try(var scope=new PeerDecodeScope()) {assertThrows(IOException.class,()->com.micaftic.morpher.molang.MolangEngine.createEmpty().parse(new StringReader(expression)));}
        assertFalse(com.micaftic.morpher.molang.MolangEngine.createEmpty().parse(expression).isEmpty());
    }
    @Test void animatedImagesAndMetadataImagesShareThePeerResourceBudget()throws Exception {
        var bytes=new ByteArrayOutputStream();var writer=javax.imageio.ImageIO.getImageWritersByFormatName("gif").next();
        try(var output=javax.imageio.ImageIO.createImageOutputStream(bytes)) {
            writer.setOutput(output);writer.prepareWriteSequence(null);var image=new java.awt.image.BufferedImage(1,1,java.awt.image.BufferedImage.TYPE_INT_ARGB);
            for(int i=0;i<257;i++)writer.writeToSequence(new javax.imageio.IIOImage(image,null,null),null);writer.endWriteSequence();
        }finally{writer.dispose();}
        assertThrows(IOException.class,()->PeerModelPreflight.image(new ByteArrayInputStream(bytes.toByteArray())));
        var png=new ByteArrayOutputStream();javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(1,1,java.awt.image.BufferedImage.TYPE_INT_ARGB),"png",png);
        var raw=new com.micaftic.morpher.resource.pojo.RawYsmModel();raw.mainEntity.mainModel=new com.micaftic.morpher.resource.pojo.RawYsmModel.RawGeometry();
        for(int i=0;i<2;i++){var avatar=new com.micaftic.morpher.resource.pojo.RawYsmModel.RawImage();avatar.width=avatar.height=4096;avatar.format=2;avatar.data=png.toByteArray();raw.metadata.extraAvatars.add(avatar);}
        assertThrows(IOException.class,()->PeerModelPreflight.validateParsed(raw));
    }
    @Test void rawExpressionsCannotBorrowACachedAstToBypassPeerComplexityLimits(){
        var raw=new com.micaftic.morpher.resource.pojo.RawYsmModel();raw.mainEntity.mainModel=new com.micaftic.morpher.resource.pojo.RawYsmModel.RawGeometry();
        var file=new com.micaftic.morpher.resource.pojo.RawYsmModel.RawAnimationFile();var animation=new com.micaftic.morpher.resource.pojo.RawYsmModel.RawAnimation();animation.blendWeight="(".repeat(40)+"1"+")".repeat(40);file.animations.put("bad",animation);raw.mainEntity.animationFiles.put("main",file);
        assertThrows(IOException.class,()->PeerModelPreflight.validateParsed(raw));
    }
    @Test void residentAdmissionIsHeldUntilExplicitReleaseAndRejectsBeforeAssemblyCreation()throws Exception {
        long cpu=PeerResidentReservation.CPU.used(),gpu=PeerResidentReservation.GPU.used();
        try(var reservation=PeerResidentReservation.reserve(1024,16,24,1)){assertTrue(PeerResidentReservation.CPU.used()>cpu);assertTrue(PeerResidentReservation.GPU.used()>gpu);assertThrows(IOException.class,()->PeerResidentReservation.reserve(PeerResidentReservation.CPU.capacity(),1,1,1));}
        assertEquals(cpu,PeerResidentReservation.CPU.used());assertEquals(gpu,PeerResidentReservation.GPU.used());
    }
}
