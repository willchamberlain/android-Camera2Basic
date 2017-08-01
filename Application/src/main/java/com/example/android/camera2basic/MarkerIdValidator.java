package com.example.android.camera2basic;

import boofcv.abst.fiducial.FiducialDetector;
import boofcv.struct.image.GrayF32;

/**
 * Created by will on 31/07/17.
 */

public class MarkerIdValidator {
    private boolean isValid;
    private FiducialDetector<GrayF32> detector;
    private int i;
    private int tag_id;

    public MarkerIdValidator(FiducialDetector<GrayF32> detector, int i, int tag_id) {
        this.detector = detector;
        this.i = i;
        this.tag_id = tag_id;
        this.isValid= false;
    }

    boolean isValid() {
        return isValid;
    }

    public int getTag_id() {
        return tag_id;
    }

    public MarkerIdValidator invoke() {
        if( detector.hasUniqueID() ) {
            System.out.println("Target ID = " + detector.getId(i));
            long tag_id_long = detector.getId(i);
            tag_id = (int)tag_id_long;
            if ((long)tag_id != tag_id_long) {
                //throw new IllegalArgumentException(l + " cannot be cast to int without changing its value.");
                System.out.println(" BoofCV: cannot use tag: tag_id_long '"+tag_id_long+"' cannot be cast to int without changing its value.");
                isValid = false;
                return this;
            }
        }
        isValid = true;
        return this;
    }
}
