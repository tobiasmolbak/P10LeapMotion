package p10.p10leapmotion;

import android.location.Location;

import java.text.DecimalFormat;
import java.util.ArrayList;

import static p10.p10leapmotion.MainActivity.ATTENTIVE;
import static p10.p10leapmotion.MainActivity.GOOD;
import static p10.p10leapmotion.MainActivity.NEGATIVE;
import static p10.p10leapmotion.MainActivity.NEUTRAL;

public class SegmentData {
    private Location startLocation;
    private Location endLocation;
    private float distance;
    private float speed;
    private String attentiveState;
    private float score;
    private ArrayList<String> attentivePredStates = new ArrayList<>();
    private ArrayList<String> rightPredStates = new ArrayList<>();
    private ArrayList<String> leftPredStates = new ArrayList<>();

    public SegmentData(Location startLocation, Location endLocation, ArrayList<String> attentivePredStates, ArrayList<String> rightPredStates, ArrayList<String> leftPredStates) {
        this.startLocation = startLocation;
        this.endLocation = endLocation;
        this.attentivePredStates = attentivePredStates;
        this.rightPredStates = rightPredStates;
        this.leftPredStates = leftPredStates;

        this.speed = calculateSpeed(startLocation, endLocation);
        this.score = calculateScore(attentivePredStates);
        this.attentiveState = calculateAttentiveState(score);
        this.distance = startLocation.distanceTo(endLocation);
    }

    public SegmentData(Location startLocation, Location endLocation, ArrayList<String> attentivePredStates, ArrayList<String> rightPredStates, ArrayList<String> leftPredStates, float speed, float score, float distance, String attentiveState) {
        this.startLocation = startLocation;
        this.endLocation = endLocation;
        this.attentivePredStates = attentivePredStates;
        this.rightPredStates = rightPredStates;
        this.leftPredStates = leftPredStates;

        this.speed = speed;
        this.score = score;
        this.attentiveState = attentiveState;
        this.distance = distance;
    }

    private float calculateSpeed(Location startLocation, Location endLocation) {
        double timeDiff = endLocation.getTime() - startLocation.getTime();
        double distDiff = startLocation.distanceTo(endLocation);

        return (float) ((distDiff / timeDiff) * 3600);
    }

    private float calculateScore(ArrayList<String> attentivePredStates) {
        float totalStates = attentivePredStates.size();
        float attentiveStates = 0;

        if (totalStates > 0) {
            for (String state : attentivePredStates) {
                if (state.equals(ATTENTIVE)) {
                    attentiveStates++;
                }
            }
            return (attentiveStates / totalStates) * 100;
        }
        return 0;
    }

    private String calculateAttentiveState(float score) {
        if (score >= 80) {
            return GOOD;
        } else if (score >= 60) {
            return NEUTRAL;
        } else {
            return NEGATIVE;
        }
    }

    private String combineArrayList(ArrayList<String> list) {
        String combinedString = "";
        Boolean firstElement = true;
        for (String element : list) {
            if (firstElement) {
                combinedString = element;
                firstElement = false;
            } else {
                combinedString = combinedString + "," + element;
            }
        }
        return combinedString;
    }

    public Location getStartLocation() {
        return startLocation;
    }

    public Location getEndLocation() {
        return endLocation;
    }

    public float getDistance() {
        return distance;
    }

    public Float getSpeed() {
        return speed;
    }

    public String getAttentiveState() {
        return attentiveState;
    }

    public float getScore() {
        return score;
    }

    public ArrayList<String> getAttentivePredStates() {
        return attentivePredStates;
    }

    public String getAttentivePredStatesString() {
        return combineArrayList(attentivePredStates);
    }

    public ArrayList<String> getRightPredStates() {
        return rightPredStates;
    }

    public String getRightPredStatesString() {
        return combineArrayList(rightPredStates);
    }

    public ArrayList<String> getLeftPredStates() {
        return leftPredStates;
    }

    public String getLeftPredStatesString() {
        return combineArrayList(leftPredStates);
    }
}
