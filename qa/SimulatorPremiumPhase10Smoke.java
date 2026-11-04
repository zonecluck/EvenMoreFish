import com.oheers.fish.fishing.simulator.presentation.PresentationArbiter;
import com.oheers.fish.fishing.simulator.presentation.UiProfile;
import com.oheers.fish.fishing.simulator.presentation.VisualPreferenceStore;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/** Plain-JDK test to run when Gradle/Paper dependencies cannot be downloaded. */
public final class SimulatorPremiumPhase10Smoke {
    private static void check(boolean condition,String label){if(!condition)throw new AssertionError(label);}
    public static void main(String[] args) throws Exception {
        UUID p=UUID.randomUUID(), cast1=UUID.randomUUID(), cast2=UUID.randomUUID();
        PresentationArbiter arbiter=new PresentationArbiter();
        arbiter.begin(p,cast1);
        check(arbiter.offer(p,cast1,PresentationArbiter.Channel.ACTION_BAR,
            PresentationArbiter.Priority.APPROACHING,"approach",Map.of(),1,30),"approach accepted");
        check(arbiter.drain(1,5).size()==1,"one approach emission");
        check(arbiter.offer(p,cast1,PresentationArbiter.Channel.ACTION_BAR,
            PresentationArbiter.Priority.BITE_READY,"bite",Map.of("time","1.3s"),2,8),"bite wins");
        check(arbiter.drain(2,5).size()==1,"bite immediately preempts approach");
        check(!arbiter.offer(p,cast1,PresentationArbiter.Channel.ACTION_BAR,
            PresentationArbiter.Priority.APPROACHING,"late-approach",Map.of(),3,8),"no late approach");
        check(arbiter.drain(3,5).isEmpty(),"dedupe identical bite");
        check(arbiter.offer(p,cast1,PresentationArbiter.Channel.ACTION_BAR,
            PresentationArbiter.Priority.BITE_READY,"bite",Map.of("time","1.2s"),4,8),"timer updated");
        check(arbiter.drain(4,5).isEmpty(),"no HUD spam within interval");
        check(arbiter.drain(7,5).size()==1,"timer updated after interval");
        check(arbiter.offer(p,cast1,PresentationArbiter.Channel.ACTION_BAR,
            PresentationArbiter.Priority.CATCH_RESULT,"caught",Map.of(),8,30),"terminal result");
        arbiter.close(p,cast1);
        check(arbiter.drain(8,5).size()==1,"terminal result survives endSession");
        check(!arbiter.offer(p,cast1,PresentationArbiter.Channel.ACTION_BAR,
            PresentationArbiter.Priority.BITE_READY,"late-bite",Map.of(),9,8),"result blocks bite");
        arbiter.begin(p,cast2);
        check(!arbiter.offer(p,cast1,PresentationArbiter.Channel.ACTION_BAR,
            PresentationArbiter.Priority.CATCH_RESULT,"old",Map.of(),10,8),"old cast rejected");
        check(arbiter.offer(p,cast2,PresentationArbiter.Channel.ACTION_BAR,
            PresentationArbiter.Priority.BITE_READY,"fresh",Map.of(),10,5),"new cast accepted");
        check(arbiter.drain(10,5).size()==1,"new cast replaces old result");
        check(arbiter.drain(15,5).isEmpty(),"expired intent removed");
        arbiter.forget(p);
        check(arbiter.sessions()==0,"quit cleanup");
        check(arbiter.blockedStaleSession()>0 && arbiter.blockedLowerPriority()>0,"drop diagnostics");

        check(!UiProfile.OFF.hud() && !UiProfile.OFF.cinematic(),"OFF truly quiet");
        check(UiProfile.CINEMATIC.hud() && UiProfile.CINEMATIC.cinematic(),"cinematic profile");
        check(!UiProfile.REDUCED_MOTION.cinematic(),"reduced motion no camera FX");
        check(UiProfile.parse("reduced-motion")==UiProfile.REDUCED_MOTION,"slug parse");

        Path folder=Files.createTempDirectory("emf-prefs-smoke-");
        VisualPreferenceStore repository=new VisualPreferenceStore(folder);
        check(repository.load(p,UiProfile.BALANCED).get(3,TimeUnit.SECONDS)==UiProfile.BALANCED,"default for missing file");
        repository.save(p,UiProfile.REDUCED_MOTION).get(3,TimeUnit.SECONDS);
        check(repository.load(p,UiProfile.BALANCED).get(3,TimeUnit.SECONDS)==UiProfile.REDUCED_MOTION,"durable reload");
        repository.save(p,UiProfile.MINIMAL).get(3,TimeUnit.SECONDS);
        repository.save(p,UiProfile.CINEMATIC).get(3,TimeUnit.SECONDS);
        check(repository.load(p,UiProfile.BALANCED).get(3,TimeUnit.SECONDS)==UiProfile.CINEMATIC,"serialized last write wins");
        check(repository.shutdown(Duration.ofSeconds(3)),"shutdown flush");
        check(Files.readString(folder.resolve(p+".yml")).contains("profile: CINEMATIC"),"plain YAML stored");
        Files.deleteIfExists(folder.resolve(p+".yml"));Files.deleteIfExists(folder);
        System.out.println("PASS: 26+ premium HUD/session/persistence assertions");
    }
}
