package sma.rhythmtapper.game;

import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Vibrator;
import android.util.Log;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

import sma.rhythmtapper.MainActivity;
import sma.rhythmtapper.framework.FileIO;
import sma.rhythmtapper.framework.Game;
import sma.rhythmtapper.framework.Graphics;
import sma.rhythmtapper.framework.Input.TouchEvent;
import sma.rhythmtapper.framework.Music;
import sma.rhythmtapper.framework.Screen;
import sma.rhythmtapper.game.models.Ball;
import sma.rhythmtapper.models.Difficulty;

public class GameScreen extends Screen {

    private static final String TAG = "GameScreenTag";

    enum GameState {
        Ready, Running, Paused, GameOver
    }


    public Game receivedGame;
    public String padNumber = "0";

    // game and device
    private int _gameHeight;
    private int _gameWidth;
    private Random _rand;
    private Difficulty _difficulty;
    private int _lifes;
    private Vibrator _vibrator;
    private boolean _isEnding;

    // score
    private int _score;
    private int _multiplier;
    private int _streak;

    // tickers
    private int _tick;
    private int _doubleMultiplierTicker;
    private int _explosionTicker;
    private float _currentTime;
    private int _endTicker;

    // balls
    private List<Ball> _ballsLeft;
    private List<Ball> _ballsMiddleLeft;
    private List<Ball> _ballsMiddleRight;
    private List<Ball> _ballsRight;

    // lane miss indicators
    private int _laneHitAlphaLeft;
    private int _laneHitAlphaMiddleLeft;
    private int _laneHitAlphaMiddleRight;
    private int _laneHitAlphaRight;

    // difficulty params
    private float _spawnInterval;
    private int _ballSpeed;
    //private final double _spawnChance_normal = 0.26; // TODO dynamic

    // audio
    private Music _currentTrack;

    // ui
    private Paint _paintScore;
    private Paint _paintGameover;

    // constants
    // how far the screen should scroll after the track ends
    private static final int END_TIME = 1800;
    // initial y coordinate of spawned balls
    private static final int BALL_INITIAL_Y = -50;
    // hitbox is the y-range within a ball can be hit by a press in its lane
    private static final int HITBOX_CENTER = 1760;
    private static final int HITBOX_HEIGHT = 200;
    // if no ball is in the hitbox when pressed, remove the lowest ball in the
    // miss zone right above the hitbox (it still counts as a missㄷ)
    private static final int MISS_ZONE_HEIGHT = 150;
    private static final int MISS_FLASH_INITIAL_ALPHA = 240;
    //from cj  Ready -> Running
    private GameState state = GameState.Ready;

    private int[][] rudi_sec = {
            {300, 300, 300, 300, 300, 300, 300, 300 },
            {800,800,800,2400},
            {400, 400, 400, 400, 400, 400, 2400},
            {}

    };
    private int[] rudiArray;//파일 받아오고 루디먼트 번호 저장
    private int currentRudi;
    private int cnt = 0;
    private int note_cnt=0;
    public long starttime;
    GameScreen(Game game, Difficulty difficulty) {
        super(game);

        starttime = System.currentTimeMillis();
        receivedGame = game;

        rudiArray = new int[2];
        rudiArray[0] = 1;
        rudiArray[1] = 2;

        currentRudi = 0;

        _difficulty = difficulty;
        // init difficulty parameters
        _ballSpeed = _difficulty.getBallSpeed();
//        _spawnInterval = _difficulty.getSpawnInterval();
//        _spawnInterval = _difficulty.getSpawnInterval()/2;

        // Initialize game objects
        _gameHeight = game.getGraphics().getHeight();
        _gameWidth = game.getGraphics().getWidth();
        _vibrator = game.getVibrator();
        _multiplier = 1;
        _doubleMultiplierTicker = 0;
        _score = 0;
        _streak = 0;
        _ballsLeft = new ArrayList<>();
        _ballsMiddleLeft = new ArrayList<>();
        _ballsMiddleRight = new ArrayList<>();
        _ballsRight = new ArrayList<>();
        _rand = new Random();
        _tick = 0;
        _endTicker = END_TIME / _difficulty.getBallSpeed();
        _currentTime = 0f;
        _explosionTicker = 0;
        _lifes = 10;
        _laneHitAlphaLeft = 0;
        _laneHitAlphaMiddleLeft = 0;
        _laneHitAlphaMiddleRight = 0;
        _laneHitAlphaRight = 0;
        _currentTrack = Assets.musicTrack;
        _isEnding = false;

        // paints for text
        _paintScore = new Paint();
        _paintScore.setTextSize(30);
        _paintScore.setTextAlign(Paint.Align.CENTER);
        _paintScore.setAntiAlias(true);
        _paintScore.setColor(Color.WHITE);

        _paintGameover = new Paint();
        _paintGameover.setTextSize(50);
        _paintGameover.setTextAlign(Paint.Align.CENTER);
        _paintGameover.setAntiAlias(true);
        _paintGameover.setColor(Color.BLACK);
    }

    @Override
    public void update(float deltaTime) {
        List<TouchEvent> touchEvents = game.getInput().getTouchEvents();
        padNumber = receivedGame.getPadNumber();

        Log.d("jh", String.valueOf(_currentTime));
        if (state == GameState.Ready){
            updateReady(touchEvents);
        }
        if (state == GameState.Running) {
            updateHitRunning(padNumber, deltaTime);
            updateRunning(touchEvents, deltaTime);
        }
        if (state == GameState.Paused)
            updatePaused(touchEvents);
        if (state == GameState.GameOver)
            updateGameOver(touchEvents);
    }

    private void updateReady(List<TouchEvent> touchEvents) {
        if (touchEvents.size() > 0||receivedGame.getPadNumber().equals("1")||receivedGame.getPadNumber().equals("2")||receivedGame.getPadNumber().equals("3")||receivedGame.getPadNumber().equals("4")){
            receivedGame.setPadNumber("0");
            state = GameState.Running;
            touchEvents.clear();
            _currentTrack.setLooping(false);
            _currentTrack.setVolume(0.25f);
            _currentTrack.play();
        }
    }

    private void updateHitRunning(String padNumber, float deltaTime) {
        handleHitEvents(padNumber);
        checkDeath();
        checkEnd();
        updateVariables(deltaTime);
    }

    private void updateRunning(List<TouchEvent> touchEvents, float deltaTime) {
        // 1. All touch input is handled here:
        handleTouchEvents(touchEvents);

        // 2. Check miscellaneous events like death:
        checkDeath();
        checkEnd();

        // 3. Individual update() methods.
        updateVariables(deltaTime);
    }

    private void checkEnd() {
        if (_currentTrack.isStopped()) {
            _isEnding = true;
        }
    }

    private void checkDeath() {
        if (_lifes <= 0) {
            endGame();
        }
    }

    private void endGame() {
        state = GameState.GameOver;
        // update highscore
        FileIO fileIO = game.getFileIO();
        SharedPreferences prefs = fileIO.getSharedPref();
        int oldScore;

        switch (_difficulty.getMode()) {
            case Difficulty.EASY_TAG:
                oldScore = prefs.getInt(Difficulty.EASY_TAG, 0);
                break;
            case Difficulty.MED_TAG:
                oldScore = prefs.getInt(Difficulty.MED_TAG, 0);
                break;
            case Difficulty.HARD_TAG:
                oldScore = prefs.getInt(Difficulty.HARD_TAG, 0);
                break;
            default:
                oldScore = 0;
                break;
        }

        if (_score > oldScore) {
            SharedPreferences.Editor editor = prefs.edit();
            editor.putInt(_difficulty.getMode(), _score);
            editor.apply();
        }
    }

    private void handleHitEvents(String padNumber) {
        if (padNumber.equals("1")) {
            if (!hitLane(_ballsLeft)) {
                // if no ball was hit
                _laneHitAlphaLeft = MISS_FLASH_INITIAL_ALPHA;
            }
        }
        if (padNumber.equals("2")) {
            if (!hitLane(_ballsMiddleLeft)) {
                // if no ball was hit
                _laneHitAlphaMiddleLeft = MISS_FLASH_INITIAL_ALPHA;
            }
        }
        if (padNumber.equals("3")) {
            if (!hitLane(_ballsMiddleRight)) {
                // if no ball was hit
                _laneHitAlphaMiddleRight = MISS_FLASH_INITIAL_ALPHA;
            }
        }
        if (padNumber.equals("4")) {
            if (!hitLane(_ballsRight)) {
                // if no ball was hit
                _laneHitAlphaRight = MISS_FLASH_INITIAL_ALPHA;
            }
        }
        receivedGame.setPadNumber("0");
    }

    private void handleTouchEvents(List<TouchEvent> touchEvents) {
        int len = touchEvents.size();

        for (int i = 0; i < len; i++) {
            TouchEvent event = touchEvents.get(i);
            if (event.type == TouchEvent.TOUCH_DOWN) {
                if (event.y > 1500) {
                    // ball hit area
                    if (event.x < _gameWidth / 4) {
                        if (!hitLane(_ballsLeft)) {
                            // if no ball was hit
                            _laneHitAlphaLeft = MISS_FLASH_INITIAL_ALPHA;
                        }
                    } else if (event.x < _gameWidth / 4 * 2) {
                        if (!hitLane(_ballsMiddleLeft)) {
                            _laneHitAlphaMiddleLeft = MISS_FLASH_INITIAL_ALPHA;
                        }
                    } else if (event.x < _gameWidth / 4 * 3) {
                        if (!hitLane(_ballsMiddleRight)) {
                            _laneHitAlphaMiddleRight = MISS_FLASH_INITIAL_ALPHA;
                        }
                    } else {
                        if (!hitLane(_ballsRight)) {
                            _laneHitAlphaRight = MISS_FLASH_INITIAL_ALPHA;
                        }
                    }
                } else {
                    // pause area
                    touchEvents.clear();
                    pause();
                    break;
                }
            }
        }
    }

    // update all the games variables each tick
    private void updateVariables(float deltatime) {
        // update timer
        _currentTime += deltatime;


        //////////////////////////
        //파일에서 값을 받아온 거 배열로 저장하고 그 배열 읽어오기
        /////////////////////////

        // update ball position

        for (Ball b : _ballsLeft) {
            b.update((int) (_ballSpeed * deltatime));
        }
        //변경부분
        for (Ball b : _ballsMiddleLeft) {
            b.update((int) (_ballSpeed * deltatime));
        }
        for (Ball b : _ballsMiddleRight) {
            b.update((int) (_ballSpeed * deltatime));
        }

        for (Ball b : _ballsRight) {
            b.update((int) (_ballSpeed * deltatime));
        }

        // remove missed balls
        if (removeMissed(_ballsLeft.iterator())) {
            _laneHitAlphaLeft = MISS_FLASH_INITIAL_ALPHA;
        }
//        if (removeMissed(_ballsMiddle.iterator())) {
//            _laneHitAlphaMiddle = MISS_FLASH_INITIAL_ALPHA;
//        }
        if (removeMissed(_ballsMiddleLeft.iterator())) {
            _laneHitAlphaMiddleLeft = MISS_FLASH_INITIAL_ALPHA;
        }
        if (removeMissed(_ballsMiddleRight.iterator())) {
            _laneHitAlphaMiddleRight = MISS_FLASH_INITIAL_ALPHA;
        }

        if (removeMissed(_ballsRight.iterator())) {
            _laneHitAlphaRight = MISS_FLASH_INITIAL_ALPHA;
        }
        // spawn new balls
//        if (!_isEnding && _currentTime % _spawnInterval <= deltatime) {
        //interval 필요 없어서 없앤 버전
        checkRudiState();

        if (!_isEnding) {

            spawnBalls(note_cnt);
        }

        // decrease miss flash intensities
        _laneHitAlphaLeft -= Math.min(_laneHitAlphaLeft, 10);
        _laneHitAlphaMiddleLeft -= Math.min(_laneHitAlphaMiddleLeft, 10);
        _laneHitAlphaMiddleRight -= Math.min(_laneHitAlphaMiddleRight, 10);
        _laneHitAlphaRight -= Math.min(_laneHitAlphaRight, 10);

        // update tickers
        _doubleMultiplierTicker -= Math.min(1, _doubleMultiplierTicker);
        _explosionTicker -= Math.min(1, _explosionTicker);
        _tick = (_tick + 1) % 100000;

        if (_isEnding) {
            _endTicker -= Math.min(1, _endTicker);

            if (_endTicker <= 0) {
                endGame();
            }
        }
    }
    private void checkRudiState(){
        switch (currentRudi+1){
            case 1:
                if (note_cnt >= 8) {
                    currentRudi++;
                    note_cnt = 0;
                }
                break;
            case 2:
                if (note_cnt >= 4){
                    currentRudi++;
                    note_cnt = 0;
                }
                break;
        }

        //Log.d("qwerqwer",String.valueOf(currentRudi));
        //Log.d("qwerqwerqwer",String.valueOf(rudiArray.length));
        if (currentRudi > rudiArray.length-1)
            currentRudi = 0;
    }

    // remove the balls from an iterator that have fallen through the hitbox
    private boolean removeMissed(Iterator<Ball> iterator) {
        while (iterator.hasNext()) {
            Ball b = iterator.next();
            if (b.y > HITBOX_CENTER + HITBOX_HEIGHT / 2) {
                iterator.remove();
                Log.d(TAG, "fail press");
                onMiss(b);

                return b.type != Ball.BallType.Skull;
            }
        }
        return false;
    }
    // handles a TouchEvent on a certain lane
    private boolean hitLane(List<Ball> balls) {
        Iterator<Ball> iter = balls.iterator();
        Ball lowestBall = null;
        while (iter.hasNext()) {
            Ball b = iter.next();
            if (lowestBall == null || b.y > lowestBall.y) {
                lowestBall = b;
            }
        }
        if (lowestBall != null && lowestBall.y > HITBOX_CENTER - HITBOX_HEIGHT / 2) {
            balls.remove(lowestBall);
            onHit(lowestBall);
            return lowestBall.type != Ball.BallType.Skull;
        } else {
            if (lowestBall != null && lowestBall.y > HITBOX_CENTER - HITBOX_HEIGHT / 2 - MISS_ZONE_HEIGHT) {
                balls.remove(lowestBall);
            }
            onMiss(null);

            return false;
        }
    }

    // triggers when a lane gets tapped that has currently no ball in its hitbox
    private void onMiss(Ball b) {
        _vibrator.vibrate(100);
        _streak = 0;
        _multiplier = 1;
        //--_lifes;// for the test
        updateMultipliers();
    }

    // triggers when a lane gets tapped that currently has a ball in its hitbox
    private void onHit(Ball b) {
        _streak++;

        updateMultipliers();
        _score += 10 * _multiplier
                * (_doubleMultiplierTicker > 0 ? 2 : 1);
    }

    // triggers after a touch event was handled by hitLane()
    private void updateMultipliers() {
        if (_streak > 80) {
            _multiplier = 10;
        } else if (_streak > 40) {
            _multiplier = 5;
        } else if (_streak > 30) {
            _multiplier = 4;
        } else if (_streak > 20) {
            _multiplier = 3;
        } else if (_streak > 10) {
            _multiplier = 2;
        } else {
            _multiplier = 1;
        }
    }

    private void spawnBalls(int note) {

        /*int rudi_sec=0;


        //        float randFloat = _rand.nextFloat();

        //시간 매개변수로 바꾸기
        if(rudi==1)
            rudi_sec=300;
        else if(rudi==2)
            rudi_sec=150;*/
        int randInt = _rand.nextInt(4);
        final int ballY = BALL_INITIAL_Y;


        if(System.currentTimeMillis()-starttime>rudi_sec[currentRudi][note]) {//여기가 문제임
            Log.d("qwer1", String.valueOf(rudi_sec[currentRudi][note_cnt]));
            Log.d("qwer11", String.valueOf(note_cnt));
            //Log.d("qwer2", String.valueOf(System.currentTimeMillis() - starttime));

            if (randInt == 0) {
                int ballX = _gameWidth / 4 / 2;
                spawnBall(_ballsLeft, randInt, ballX, ballY);
            } else if (randInt == 1) {
                int ballX = _gameWidth / 3;
                spawnBall(_ballsMiddleLeft, randInt, ballX, ballY);
            } else if (randInt == 2) {
                int ballX = _gameWidth - _gameWidth / 3;
                spawnBall(_ballsMiddleRight, randInt, ballX, ballY);
            } else {
                int ballX = _gameWidth - _gameWidth / 4 / 2;
                spawnBall(_ballsRight, randInt, ballX, ballY);
            }
            starttime = System.currentTimeMillis();
            note_cnt++;
        }

        //Log.d("qwer", String.valueOf(note_cnt));
    }

    private void spawnBall(List<Ball> balls, float randFloat, int ballX, int ballY) {

        balls.add(0, new Ball(ballX, ballY, Ball.BallType.Normal));
        starttime=System.currentTimeMillis();
        cnt++;
        //        if (randFloat < _spawnChance_normal) {
//            balls.add(0, new Ball(ballX, ballY, Ball.BallType.Normal));
//        }
    }

    private void updatePaused(List<TouchEvent> touchEvents) {
        if (_currentTrack.isPlaying()) {
            _currentTrack.pause();
        }

        int len = touchEvents.size();
        for (int i = 0; i < len; i++) {
            TouchEvent event = touchEvents.get(i);
            if (event.type == TouchEvent.TOUCH_DOWN) {
                resume();
                return;
            }
        }
    }

    private void updateGameOver(List<TouchEvent> touchEvents) {
        if (!_currentTrack.isStopped()) {
            _currentTrack.stop();
        }

        int len = touchEvents.size();
        for (int i = 0; i < len; i++) {
            TouchEvent event = touchEvents.get(i);
            if (event.type == TouchEvent.TOUCH_UP) {
                if (event.x > 300 && event.x < 540 && event.y > 845
                        && event.y < 1100) {
                    game.goToActivity(MainActivity.class);
                    return;
                } else if (event.x >= 540 && event.x < 780 && event.y > 845
                        && event.y < 1100) {
                    game.setScreen(new LoadingScreen(game, _difficulty));
                }
            }
        }
    }

    @Override
    public void paint(float deltaTime) {
        Graphics g = game.getGraphics();

        // First draw the game elements.
        g.drawImage(Assets.background, 0, 0);

        g.drawRect(0, 0, _gameWidth / 4 + 1, _gameHeight, Color.argb(_laneHitAlphaLeft, 255, 0, 0));
        g.drawRect(_gameWidth / 4, 0, _gameWidth / 4 + 1, _gameHeight, Color.argb(_laneHitAlphaMiddleLeft, 255, 0, 0));
        g.drawRect(_gameWidth / 4 * 2, 0, _gameWidth / 4 + 1, _gameHeight, Color.argb(_laneHitAlphaMiddleRight, 255, 0, 0));
        g.drawRect(_gameWidth / 4 * 3, 0, _gameWidth / 4 + 1, _gameHeight, Color.argb(_laneHitAlphaRight, 255, 0, 0));

        for (Ball b : _ballsLeft) {
            paintBall(g, b);
        }
        for (Ball b : _ballsMiddleLeft) {
            paintBall(g, b);
        }
        for (Ball b : _ballsMiddleRight) {
            paintBall(g, b);
        }

        for (Ball b : _ballsRight) {
            paintBall(g, b);
        }
        // Secondly, draw the UI above the game elements.
        if (state == GameState.Ready)
            drawReadyUI();
        if (state == GameState.Running)
            drawRunningUI();
        if (state == GameState.Paused)
            drawPausedUI();
        if (state == GameState.GameOver)
            drawGameOverUI();
    }

    private void paintBall(Graphics g, Ball b) {
        switch (b.type) {
            case Normal:
                g.drawImage(Assets.ballNormal, b.x - 90, b.y - 90);
                break;
        }
    }

    private void drawReadyUI() {
        Graphics g = game.getGraphics();

        g.drawARGB(155, 0, 0, 0);
        g.drawString("Tap to start!", 540, 500, _paintScore);
    }

    private void drawRunningUI() {
        Graphics g = game.getGraphics();

        if (_doubleMultiplierTicker > 0) {
            g.drawImage(Assets.sirens, 0, 100);
        }
        g.drawRect(0, 0, _gameWidth, 100, Color.BLACK);

        padNumber = receivedGame.getPadNumber();

        String s = "Score: " + _score +
                "   Lifes remaining: " + _lifes +
                "   DrumPadNumber: " + padNumber + " " + currentRudi;
        g.drawString(s, 600, 80, _paintScore);
    }

    private void drawPausedUI() {
        Graphics g = game.getGraphics();
        g.drawARGB(155, 0, 0, 0);
        g.drawImage(Assets.pause, 200, 500);
        g.drawString("TAP TO CONTINUE", 540, 845, _paintGameover);
    }

    private void drawGameOverUI() {
        Graphics g = game.getGraphics();
        g.drawARGB(205, 0, 0, 0);
        g.drawImage(Assets.gameover, 200, 500);
        g.drawString("FINAL SCORE: " + _score, 540, 845, _paintGameover);
    }

    @Override
    public void pause() {
        if (state == GameState.Running) {
            state = GameState.Paused;
            _currentTrack.pause();
        }
    }

    @Override
    public void resume() {
        if (state == GameState.Paused) {
            state = GameState.Running;
            _currentTrack.play();
        }
    }

    @Override
    public void dispose() {
        if (_currentTrack.isPlaying()) {
            _currentTrack.stop();
        }
    }

    @Override
    public void backButton() {
        dispose();
    }
}