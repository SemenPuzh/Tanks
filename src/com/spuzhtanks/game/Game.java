
package com.spuzhtanks.game;

import java.awt.Graphics2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import com.spuzhtanks.IO.Input;
import com.spuzhtanks.display.Display;
import com.spuzhtanks.game.level.Level;
import com.spuzhtanks.graphics.TextureAtlas;
import com.spuzhtanks.utils.Time;
import com.spuzhtanks.utils.Utils;

public class Game implements Runnable {

	public static final int							WIDTH			= 624;
	public static final int							HEIGHT			= 624;
	public static final String						TITLE			= "Tanks";
	public static final int							CLEAR_COLOR		= 0xff000000;
	public static final int							NUM_BUFFERS		= 3;
	public static final float						UPDATE_RATE		= 60.0f;
	public static final float						UPDATE_INTERVAL	= Time.SECOND / UPDATE_RATE;
	public static final float						SCALE			= 3f;
	public static final long						IDLE_TIME		= 1;
	public static final String						ATLAS_FILE_NAME	= "texture_atlas.png";
	private static final float						PLAYER_SPEED	= 3f;

	private static final int						FREEZE_TIME		= 8000;
	private static List<Enemy>						enemyList		= new LinkedList<>();
	private static int								stage			= 1;

	private static Map<EntityType, List<Bullet>>	bullets;
	private static Graphics2D						graphics;
	private static boolean							enemiesFrozen;
	private static long								freezeImposedTime;

	private boolean									running;
	private Thread									gameThread;
	private Input									input;
	private static TextureAtlas						atlas;
	private static Player							player;
	private static Level							lvl;
	private static int								enemyCount;
	private boolean									canCreateEnemy;
	private static boolean							gameOver;
	private BufferedImage							gameOverImage;
	private long									timeWin;

	public Game() {
		running = false;
		Display.create(WIDTH + 8 * Level.SCALED_TILE_SIZE, HEIGHT, TITLE, CLEAR_COLOR, NUM_BUFFERS);
		graphics = Display.getGraphics();
		input = new Input();
		Display.addInputListener(input);
		atlas = new TextureAtlas(ATLAS_FILE_NAME);
		bullets = new HashMap<>();
		bullets.put(EntityType.Player, new LinkedList<Bullet>());
		bullets.put(EntityType.Enemy, new LinkedList<Bullet>());
		lvl = new Level(atlas, stage);
		player = new Player(SCALE, PLAYER_SPEED, atlas, lvl);
		enemiesFrozen = false;
		enemyCount = 20;
		timeWin = 0;
		gameOver = false;
		gameOverImage = Utils.resize(
				atlas.cut(36 * Level.TILE_SCALE, 23 * Level.TILE_SCALE, 4 * Level.TILE_SCALE, 2 * Level.TILE_SCALE),
				4 * Level.SCALED_TILE_SIZE, 2 * Level.SCALED_TILE_SIZE);
		for (int i = 0; i < gameOverImage.getHeight(); i++)
			for (int j = 0; j < gameOverImage.getWidth(); j++) {
				int pixel = gameOverImage.getRGB(j, i);
				if ((pixel & 0x00FFFFFF) < 10)
					gameOverImage.setRGB(j, i, (pixel & 0x00FFFFFF));
			}

	}

	public synchronized void start() {

		if (running)
			return;

		running = true;
		gameThread = new Thread(this);
		gameThread.start();

	}

	public synchronized void stop() {

		if (!running)
			return;

		running = false;

		try {
			gameThread.join();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}

		cleanUp();

	}

	private void update() {

		if (enemyList.size() == 0 && enemyCount == 0 && timeWin == 0)
			timeWin = System.currentTimeMillis();

		if (enemyList.size() == 0 && enemyCount == 0 && player.hasMoreLives() && !gameOver)
			nextLevel();

		canCreateEnemy = true;

		if (enemyList.size() < 4 && enemyCount > 0) {
			Random rand = new Random();
			float possibleX = rand.nextInt(3) * ((Game.WIDTH - Player.SPRITE_SCALE * Game.SCALE) / 2);
			Rectangle2D.Float recForX = new Rectangle2D.Float(possibleX, 0, Player.SPRITE_SCALE * Game.SCALE,
					Player.SPRITE_SCALE * Game.SCALE);
			for (Enemy enemy : enemyList) {
				if (enemy.isEvolving()) {
					canCreateEnemy = false;
					break;
				}

				if (canCreateEnemy)
					if (recForX.intersects(enemy.getRectangle())) {
						canCreateEnemy = false;
					}
			}
			if (canCreateEnemy) {
				if (player != null)
					if (recForX.intersects(player.getRectangle())) {
						canCreateEnemy = false;
					}
				if (canCreateEnemy) {
					Enemy enemy = null;
					enemyCount--;
					if (stage == 1) {
						if (enemyCount < 3)
							enemy = new EnemyInfantryVehicle(possibleX, 0, SCALE, atlas, lvl);
						else
							enemy = new EnemyTank(possibleX, 0, SCALE, atlas, lvl);
					} else {
						Random random = new Random();
						switch (random.nextInt(4)) {
						case 0:
							enemy = new EnemyInfantryVehicle(possibleX, 0, SCALE, atlas, lvl);
							break;
						case 1:
							enemy = new EnemyGreenTank(possibleX, 0, SCALE, atlas, lvl);
							break;
						default:
							enemy = new EnemyTank(possibleX, 0, SCALE, atlas, lvl);
						}
					}
					enemy.setPlayer(player);
					enemyList.add(enemy);
					;
				}
			}
		}

		List<Bullet> playerBulletList = getBullets(EntityType.Player);
		if (playerBulletList.size() > 0) {
			for (Enemy enemy : enemyList) {
				if (enemy.isEvolving())
					continue;
				if (enemy.getRectangle().intersects(playerBulletList.get(0).getRectangle())
						&& playerBulletList.get(0).isActive()) {
					enemy.fixHitting(Player.getPlayerStrength());
					playerBulletList.get(0).setInactive();
					if (!enemy.hasMoreLives())
						enemy.setDead();
				}
			}
		}

		if (enemiesFrozen) {
			if (System.currentTimeMillis() > freezeImposedTime + FREEZE_TIME)
				enemiesFrozen = false;
		} else {
			for (Enemy enemy : enemyList)
				enemy.update(input);
		}

		for (int i = 0; i < bullets.get(EntityType.Enemy).size(); i++)
			bullets.get(EntityType.Enemy).get(i).update();

		for (int i = 0; i < bullets.get(EntityType.Player).size(); i++)
			bullets.get(EntityType.Player).get(i).update();

		if (player != null && !player.hasMoreLives())
			player = null;

		if (player != null)
			player.update(input);

	}

	private void nextLevel() {

		if (timeWin == 0 || System.currentTimeMillis() < timeWin + 5000)
			return;

		bullets = new HashMap<>();
		bullets.put(EntityType.Player, new LinkedList<Bullet>());
		bullets.put(EntityType.Enemy, new LinkedList<Bullet>());
		if (++stage > 3)
			stage = 1;
		lvl = new Level(atlas, stage);
		enemiesFrozen = false;
		enemyCount = 20;
		enemyList = new LinkedList<>();
		player.moveOnNextLevel();
		timeWin = 0;

	}

	private void render() {

		Display.clear();

		
		lvl.render(graphics);

		
		if (player != null) {
			if (!player.isAlive()) {
				player.drawExplosion(graphics);
			} else
				player.render(graphics);
		}
		
		

		for (int i = 0; i < enemyList.size(); i++) {
			if (!enemyList.get(i).isAlive()) {
				enemyList.get(i).drawExplosion(graphics);
				enemyList.remove(i);
			}
		}
		
		

		for (Enemy enemy : enemyList)
			enemy.render(graphics);
		
		

		for (int i = 0; i < bullets.get(EntityType.Enemy).size(); i++)
			bullets.get(EntityType.Enemy).get(i).render(graphics);
		
		
		for (Bullet bullet : getBullets(EntityType.Player))
			bullet.render(graphics);
		
		

		lvl.renderGrass(graphics);
		
		

		if (gameOver) {
			graphics.drawImage(gameOverImage, Game.WIDTH / 2 - 2 * Level.SCALED_TILE_SIZE, Game.HEIGHT / 2, null);

		}
		Display.swapBuffers();

	}

	public void run() {

		int fps = 0;
		int upd = 0;
		int updl = 0;

		long count = 0;

		float delta = 0;

		long lastTime = Time.get();
		while (running) {
			long now = Time.get();
			long elapsedTime = now - lastTime;
			lastTime = now;

			count += elapsedTime;

			boolean render = false;
			delta += (elapsedTime / UPDATE_INTERVAL);

			while (delta > 1) {
				update();
				upd++;
				delta--;
				if (render) {
					updl++;
				} else {
					render = true;
				}
			}

			if (render) {
				render();
				fps++;
			} else {
				try {
					Thread.sleep(IDLE_TIME);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}

			if (count >= Time.SECOND) {
				Display.setTitle(TITLE + " || Fps: " + fps + " | Upd: " + upd + " | Updl: " + updl);
				upd = 0;
				fps = 0;
				updl = 0;
				count = 0;
			}

		}

	}

	private void cleanUp() {Display.destroy();
	}

	public static List<Enemy> getEnemies() {
		return enemyList;
	}

	public static void registerBullet(EntityType type, Bullet bullet) {
		bullets.get(type).add(bullet);
	}

	public static void unregisterBullet(EntityType type, Bullet bullet) {
		if (bullets.get(type).size() > 0) {
			bullets.get(type).remove(bullet);
		}
	}

	public static List<Bullet> getBullets(EntityType type) {
		return bullets.get(type);
	}

	public static void freezeEnemies() {
		enemiesFrozen = true;
		freezeImposedTime = System.currentTimeMillis();

	}

	public static void detonateEnemies() {
		for (Enemy enemy : enemyList)
			enemy.setDead();

	}

	public static int getEnemyCount() {
		return enemyCount;
	}

	public static void setGameOver() {
		gameOver = true;

	}

	public static void reset() {

		bullets = new HashMap<>();
		bullets.put(EntityType.Player, new LinkedList<Bullet>());
		bullets.put(EntityType.Enemy, new LinkedList<Bullet>());
		stage = 1;
		lvl = new Level(atlas, stage);
		enemiesFrozen = false;
		enemyCount = 20;
		enemyList = new LinkedList<>();
		player = new Player(SCALE, PLAYER_SPEED, atlas, lvl);
		gameOver = false;

	}

}
