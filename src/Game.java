import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.*;

import javax.sound.sampled.*;
import javax.swing.*;
import javax.swing.Timer;

public class Game extends JPanel {
	private static final long serialVersionUID = -9L;
	boolean running = true;
	Thread animator;

	int totalFrameCount = 0;
	int current_fps = 0;
	int fps_adjust = 0;

	final int target_fps = 60;
	final int ball_speed = 4;

	final double spdf = 60.0 / target_fps;

	final long optimal_time = 1000000000 / target_fps;
	final int orig_scr_wid = 800;
	final int orig_scr_hgt = 600;

	private double lastUpdateTime = System.nanoTime();
	private int pos_x = 0;
	private int pos_y = 0;
	private double xscale = 1.0;
	private double yscale = 1.0;
	private double camx = 16;
	private double camy = 16;

	private int score = 0;
	private double draw_score = 0;
	private double scr_li = 0.0;
	private double force = 1.5;

	private boolean vk_right = false;
	private boolean vk_left = false;

	private Ball ball;
	private Paddle paddle;
	private int screen_width = 800;
	private int screen_height = 600;
	private ArrayList<Block> blockList;
	private ArrayList<Particle> particleList;
	private ArrayList<DeadBlock> dbList;

	private float left_wall = 0;
	private float top_wall = 0;
	private float right_wall = 0;

	Color c_orange = new Color(255, 128, 0);
	Color c_purple = new Color(128, 0, 128);
	Color c_blue = new Color(0, 0, 255);

	public static synchronized void playSound(final String url, float p,
			float v, int reserve) {
		new Thread(new Runnable() {
			// The wrapper thread is unnecessary, unless it blocks on the
			// Clip finishing; see comments.
			public void run() {
				try {
					AudioFormat format = new AudioFormat(
							AudioFormat.Encoding.PCM_SIGNED, 44100, 16, 2, 4,
							44100, false);

					DataLine.Info info = new DataLine.Info(
							SourceDataLine.class, format);

					SourceDataLine audioLine = (SourceDataLine) AudioSystem
							.getLine(info);

					audioLine.open(format);

					audioLine.start();

					byte[] bytes = new byte[reserve];
					// ClassLoader classLoader =
					// Thread.currentThread().getContextClassLoader();
					AudioInputStream audioStream = AudioSystem
							.getAudioInputStream(getClass().getResource(url));
					audioLine.getControls();

					FloatControl pan = (FloatControl) audioLine
							.getControl(FloatControl.Type.PAN);
					pan.setValue(p);

					FloatControl vol = (FloatControl) audioLine
							.getControl(FloatControl.Type.MASTER_GAIN);
					vol.setValue(((vol.getMinimum()) + (vol.getMaximum() - vol
							.getMinimum()) * v));

					audioStream.read(bytes);
					audioLine.write(bytes, 0, bytes.length);

				} catch (UnsupportedAudioFileException ex) {
					System.out
							.println("The specified audio file is not supported.");
					ex.printStackTrace();
				} catch (LineUnavailableException ex) {
					System.out
							.println("Audio line for playing back is unavailable.");
					ex.printStackTrace();
				} catch (IOException ex) {
					System.out.println("Error playing the audio file.");
					ex.printStackTrace();
				}

			}
		}).start();
	}

	public void beep1() {
		float pan = (float) (-1.0 + 2.0 * (ball.x / orig_scr_wid));
		int idx = (int) (4 * Math.random());
		playSound("sounds/blip" + idx + ".wav", (float) (pan * 0.85), 0.8f,
				14000);
	}

	public void beep2() {
		float pan = (float) (-1.0 + 2.0 * (ball.x / orig_scr_wid));
		int idx = (int) (3 * Math.random());
		playSound("sounds/push" + idx + ".wav", (float) (pan * 0.85), 0.7f,
				24000);
	}

	public void beep3() {
		float pan = (float) (-1.0 + 2.0 * (ball.x / orig_scr_wid));
		playSound("sounds/blast.wav", (float) (pan * 0.85), 0.8f, 14000);
	}

	public void beepDead() {
		playSound("sounds/ded.wav", 0.0f, 0.8f, 240000);
	}

	ActionListener updateFPS = new ActionListener() {
		@Override
		public void actionPerformed(ActionEvent event) {
			current_fps = totalFrameCount;
			if (current_fps + 1 < target_fps) {
				fps_adjust--;
			} else if (current_fps - 1 > target_fps) {
				fps_adjust++;
			}
			totalFrameCount = 0;
		}
	};

	public static Color blend(Color c0, Color c1, double weight) {
		double weight0 = 1 - weight;
		double r = weight0 * c0.getRed() + weight * c1.getRed();
		double g = weight0 * c0.getGreen() + weight * c1.getGreen();
		double b = weight0 * c0.getBlue() + weight * c1.getBlue();
		double a = Math.max(c0.getAlpha(), c1.getAlpha());

		return new Color((int) r, (int) g, (int) b, (int) a);
	}

	private float dist(double d, double e, double camx2, double camy2) {
		return (float) Math.sqrt(Math.pow(camx2 - d, 2)
				+ Math.pow(camy2 - e, 2));
	}

	private boolean collisionRectRect(double AX1, double AY1, double AX2,
			double AY2, double BX1, double BY1, double BX2, double BY2) {
		return (AX1 < BX2 && AX2 > BX1 && AY1 < BY2 && AY2 > BY1);
	}

	private boolean collisionCircleLine(double circleCenterX,
			double circleCenterY, double circleRadius, double lineAX,
			double lineAY, double lineBX, double lineBY) {

		double lineSize = Math.sqrt(Math.pow(lineAX - lineBX, 2)
				+ Math.pow(lineAY - lineBY, 2));
		double distance;

		if (lineSize == 0) {
			distance = Math.sqrt(Math.pow(circleCenterX - lineAX, 2)
					+ Math.pow(circleCenterY - lineAY, 2));
			return distance < circleRadius;
		}

		double u = ((circleCenterX - lineAX) * (lineBX - lineAX) + (circleCenterY - lineAY)
				* (lineBY - lineAY))
				/ (lineSize * lineSize);

		if (u < 0) {
			distance = Math.sqrt(Math.pow(circleCenterX - lineAX, 2)
					+ Math.pow(circleCenterY - lineAY, 2));
		} else if (u > 1) {
			distance = Math.sqrt(Math.pow(circleCenterX - lineBX, 2)
					+ Math.pow(circleCenterY - lineBY, 2));
		} else {
			double ix = lineAX + u * (lineBX - lineAX);
			double iy = lineAY + u * (lineBY - lineAY);
			distance = Math.sqrt(Math.pow(circleCenterX - ix, 2)
					+ Math.pow(circleCenterY - iy, 2));
		}

		return distance < circleRadius;
	}

	private double angleDifference(double angle1, double angle2) {
		return ((((angle1 - angle2) % (2 * Math.PI)) + (3 * Math.PI)) % (2 * Math.PI))
				- (Math.PI);
	}

	private class DeadBlock extends Particle {
		private double X1;
		private double Y1;
		private double X2;
		private double Y2;
		private double X3;
		private double Y3;
		private double X4;
		private double Y4;

		public DeadBlock(int x1, int y1, int x2, int y2, int x3, int y3,
				int x4, int y4, double hsp, double vsp, int li) {
			super(hsp, vsp, hsp, vsp, li, Color.RED);
			this.X1 = x1;
			this.Y1 = y1;
			this.X2 = x2;
			this.Y2 = y2;
			this.X3 = x3;
			this.Y3 = y3;
			this.X4 = x4;
			this.Y4 = y4;
			this.gravity = 0 * spdf;
		}

		public void update() {
			this.life--;
			if (this.life <= 0) {
				this.destroy();
			}
			this.ysp += this.gravity;
			this.x += this.xsp * spdf;
			this.y += this.ysp * spdf;
		}

		public void destroy() {
			this.destroy = true;
		}

		public void render(Graphics2D g2) {
			g2.setColor(new Color(128, 0, 0, Math.min(
					(int) (255.0 * (int) (1 + Math.sin(4 * (float) (this.life)
							/ (float) (this.maxlife)))), 255)));

			int[] x_points = { (int) (this.X1 + this.x),
					(int) (this.X2 + this.x), (int) (this.X3 + this.x),
					(int) (this.X4 + this.x) };
			int[] y_points = { (int) (this.Y1 + this.y),
					(int) (this.Y2 + this.y), (int) (this.Y3 + this.y),
					(int) (this.Y4 + this.y) };
			g2.fillPolygon(x_points, y_points, 4);
			g2.setColor(Color.BLACK);
			g2.setStroke(new BasicStroke((this.maxlife - this.life)));
			g2.drawPolygon(x_points, y_points, 4);
		}
	}

	private class Particle {
		protected double xsp;
		protected double ysp;
		protected double x;
		protected double y;
		protected double gravity;
		protected int life;
		protected int maxlife;
		protected boolean destroy;
		protected Color col;

		public Particle(double sx, double sy, double hsp, double vsp, int li,
				Color c) {
			this.x = sx;
			this.y = sy;
			this.xsp = hsp;
			this.ysp = vsp;
			this.life = (int) (li / spdf);
			this.destroy = false;
			this.maxlife = this.life;
			this.gravity = 0.25 * spdf;
			this.col = c;
		}

		public void update() {
			this.life--;
			if (this.life <= 0) {
				this.destroy();
			}
			this.ysp += this.gravity;
			this.x += this.xsp * spdf;
			this.y += this.ysp * spdf;
		}

		public void destroy() {
			this.destroy = true;
		}

		public void render(Graphics2D g2) {
			double sp = dist(0, 0, this.xsp, this.ysp);
			if (sp > 4) {
				g2.setStroke(new BasicStroke(2));
			} else if (sp > 2) {
				g2.setStroke(new BasicStroke(2.5f));
			} else {
				g2.setStroke(new BasicStroke(3));
			}

			g2.setColor(new Color(
					this.col.getRed(),
					this.col.getGreen(),
					this.col.getBlue(),
					Math.min(
							(int) (2550.0 * ((float) (this.life) / (float) (this.maxlife))),
							255)));
			g2.drawLine((int) this.x, (int) this.y, (int) (this.x - this.xsp),
					(int) (this.y - this.ysp));
		}
	}

	private class Line {
		private int AX;
		private int AY;
		private int BX;
		private int BY;
		private Block rect;

		private class Point {
			double x;
			double y;

			public Point(double d, double e) {
				this.x = d;
				this.y = e;
			}
		}

		public Line(Block r, int ax, int ay, int bx, int by) {

			Point A = rotateAbout(new Point(ax, ay), new Point(r.x + r.width
					/ 2, r.y + r.height / 2), r.angle);
			Point B = rotateAbout(new Point(bx, by), new Point(r.x + r.width
					/ 2, r.y + r.height / 2), r.angle);

			AX = (int) A.x;
			AY = (int) A.y;
			BX = (int) B.x;
			BY = (int) B.y;
			rect = r;
		}

		private Point rotateAbout(Point p, Point c, double theta) {
			double tempX = p.x - c.x;
			double tempY = p.y - c.y;
			double rotatedX = tempX * Math.cos(theta) - tempY * Math.sin(theta);
			double rotatedY = tempX * Math.sin(theta) + tempY * Math.cos(theta);
			tempX = rotatedX + c.x;
			tempY = rotatedY + c.y;
			return new Point(tempX, tempY);
		}
	}

	private class Rect {
		protected int x;
		protected int y;
		protected int width;
		protected int height;
		protected double angle;

		public Rect(int x, int y, int width, int height) {
			this.x = x;
			this.y = y;
			this.width = width;
			this.height = height;
			this.angle = 0;
		}

		public Rect(int x, int y, int width, int height, double a) {
			this.x = x;
			this.y = y;
			this.width = width;
			this.height = height;
			this.angle = a;
		}
	}

	private class Block extends Rect {
		private int hp;
		protected Line[] lines;

		private int minX = this.x;
		private int maxX = this.x + this.width;
		private int minY = this.y;
		private int maxY = this.y + this.height;

		private Block prev;
		private Block next;
		private double light;
		private double amp;
		private int delay;
		private boolean destroy;

		private void destroy() {
			if (this.prev != null)
				this.getPrev().setNext(null);
			if (this.next != null)
				this.getNext().setPrev(null);

			this.destroy = true;
		}

		private void lightUpPrev(double li, int delay) {
			if (li < 0.05)
				return;
			this.amp = li;
			this.light = 1.0;
			this.delay = (int) (delay / spdf);
			if (this.light > 1)
				this.light = 1;
			if (this.getPrev() != null)
				this.getPrev().lightUpPrev(li / 1.5, delay + 5);
		}

		private void lightUpNext(double li, int delay) {
			if (li < 0.05)
				return;
			this.amp = li;
			this.light = 1.0;
			this.delay = (int) (delay / spdf);
			if (this.light > 1)
				this.light = 1;
			if (this.getNext() != null)
				this.getNext().lightUpNext(li / 1.5, delay + 5);
		}

		public void hit() {
			this.light = 1.0;
			this.amp = 1.0;
			scr_li = 1.0;
			score += 100;

			if (this.prev != null)
				this.getPrev().lightUpPrev(1.0 / 1.5, 2);
			if (this.next != null)
				this.getNext().lightUpNext(1.0 / 1.5, 2);

			this.hp--;
			if (this.hp <= 0) {
				this.destroy();
				score += 150;
			}
		}

		public void update() {
			if (delay > 0) {
				delay--;
			} else {
				if (light > 0) {

					light -= 0.15 * spdf;
				}
			}
			if (light < 0) {
				light = 0;
			}
		}

		public Block(int x, int y, int width, int height, int hp, double a) {
			super(x, y, width, height, a);
			this.hp = hp;
			this.prev = null;
			this.next = null;
			this.light = 0;
			this.amp = 0;
			this.delay = 0;
			this.destroy = false;
			this.lines = new Line[] {
					new Line(this, this.x, this.y, this.x + this.width, this.y),
					new Line(this, this.x + this.width, this.y, this.x
							+ this.width, this.y + this.height),
					new Line(this, this.x + this.width, this.y + this.height,
							this.x, this.y + this.height),
					new Line(this, this.x, this.y + this.height, this.x, this.y) };

			for (Line l : this.lines) {
				if (l.AX < this.minX)
					this.minX = l.AX;
				else if (l.AX > this.maxX)
					this.maxX = l.AX;
				if (l.AY < this.minY)
					this.minY = l.AY;
				else if (l.AY > this.maxY)
					this.maxY = l.AY;
			}
		}

		public Block(int x1, int y1, int x2, int y2, int x3, int y3, int x4,
				int y4, int hp) {
			super(x1, y1, 1, 1, 0);
			this.hp = hp;
			this.prev = null;
			this.next = null;
			this.light = 0;
			this.amp = 0;
			this.delay = 0;
			this.destroy = false;
			this.lines = new Line[] { new Line(this, x1, y1, x2, y2),
					new Line(this, x2, y2, x3, y3),
					new Line(this, x3, y3, x4, y4),
					new Line(this, x4, y4, x1, y1) };

			for (Line l : this.lines) {
				if (l.AX < this.minX)
					this.minX = l.AX;
				else if (l.AX > this.maxX)
					this.maxX = l.AX;
				if (l.AY < this.minY)
					this.minY = l.AY;
				else if (l.AY > this.maxY)
					this.maxY = l.AY;
			}
		}

		public Block getNext() {
			return this.next;
		}

		public void setNext(Block next) {
			this.next = next;
		}

		public Block getPrev() {
			return this.prev;
		}

		public void setPrev(Block prev) {
			this.prev = prev;
		}

		public void render(Graphics2D g2) {
			Color c = getColorForSwitch(this.hp);
			double li = this.amp * Math.sin(this.light * Math.PI);

			g2.setColor(blend(c, Color.WHITE, li));

			int[] x_points = { this.lines[0].AX, this.lines[1].AX,
					this.lines[2].AX, this.lines[3].AX };
			int[] y_points = { this.lines[0].AY, this.lines[1].AY,
					this.lines[2].AY, this.lines[3].AY };

			g2.fillPolygon(x_points, y_points, 4);
			g2.setColor(Color.BLACK);

			for (Line l : this.lines) {
				g2.setStroke(new BasicStroke((float) (6 - 6 * li)));
				g2.drawLine(l.AX, l.AY, l.BX, l.BY);
			}

		}
	}

	private class Paddle extends Rect {
		private int hsp;
		private double light;
		private int mouse;

		public Paddle(int x, int y, int width, int height) {
			super(x, y, width, height);
			this.hsp = (int) (8 * spdf);
			this.light = 0;
			this.mouse = 0;
		}

		private void moveTo(int dx) {
			if (Math.abs(dx - this.x) > this.hsp) {
				this.x += this.hsp * Math.signum((double) (dx - this.x));
			} else {
				this.x = dx;
				mouse = 0;
			}

			if (this.x < 0) {
				this.x = 0;
			} else if (this.x + this.width > orig_scr_wid) {
				this.x = orig_scr_wid - this.width;
			}
		}

		public void update() {
			if (this.mouse > 0) {
				this.mouse--;
				paddle.moveTo(pos_x - this.width / 2);
			}

			if (vk_left && !vk_right) {
				paddle.moveTo(this.x - this.hsp);
			} else if (!vk_left && vk_right) {
				paddle.moveTo(this.x + this.hsp);
			}

			if (light > 0) {
				light -= 0.05 * spdf;
			}

			if (light < 0) {
				light = 0;
			}
		}

		public void render(Graphics2D g2) {
			g2.setColor(blend(Color.GRAY, Color.WHITE, this.light));
			g2.fillRect((int) (this.x - 8 * this.light),
					(int) (this.y + 2 * this.light),
					(int) (this.width + 16 * this.light),
					(int) (this.height - 2 * this.light));
		}
	}

	private class Ball {
		private double x;
		private double y;
		private double xsp;
		private double ysp;
		private int radius;
		private boolean shot;

		public Ball(double xx, double yy) {
			this.x = xx;
			this.y = yy;
			this.radius = 4;

			this.xsp = 0;
			this.ysp = 0;
			this.shot = false;
		}

		private void transformDirection(double N) {

			double c_speed = dist(0, 0, this.xsp, this.ysp);
			double c_angle = Math.atan2(this.ysp, this.xsp);
			double e_angle = 2 * N - c_angle - Math.PI;
			this.xsp = c_speed * Math.cos(e_angle);
			this.ysp = c_speed * Math.sin(e_angle);
			camx += force * Math.cos(N);
			camy += force * Math.sin(N);

		}

		private void createSparks(double N, Color C) {

			double pdir = 0;
			int amount = (int) (5 + 10 * Math.random());
			for (int i = 0; i < amount; i++) {
				pdir = (-N) / 1 - (Math.PI * 0.35) + (Math.PI * 0.7)
						* Math.random();
				double pspeed = 6 * Math.random();
				Particle p = new Particle(this.x + this.radius
						* Math.cos(-pdir), this.y + this.radius
						* Math.sin(-pdir), pspeed * Math.cos(pdir), pspeed
						* Math.sin(pdir), (int) (30 * Math.random()), C);
				particleList.add(p);
			}
		}

		private void setDirection(double N) {
			double c_speed = dist(0, 0, this.xsp, this.ysp);
			double e_angle = N;

			this.xsp = c_speed * Math.cos(e_angle);
			this.ysp = c_speed * Math.sin(e_angle);
		}

		private Line checkPlace(double cx, double cy) {
			ArrayList<Line> lineList = new ArrayList<Line>();
			for (Block b : blockList) {
				if (collisionRectRect(this.x - this.radius + cx, this.y
						- this.radius + cy, this.x + this.radius + cx, this.y
						+ this.radius + cy, b.minX, b.minY, b.maxX, b.maxY)) {
					for (Line l : b.lines) {
						if (collisionCircleLine(this.x + cx, this.y + cy,
								this.radius, l.AX, l.AY, l.BX, l.BY)) {
							lineList.add(l);
						}
					}
				}
			}

			if (lineList.size() == 1) {
				return lineList.get(0);
			} else if (lineList.size() > 1) {
				Line champ = lineList.get(0);
				for (Line l : lineList) {
					double a1 = Math.atan2(champ.BY - champ.AY, champ.BX
							- champ.AX);
					double a2 = Math.atan2(l.BY - l.AY, l.BX - l.AX);
					double c = Math.atan2(ball.ysp, ball.xsp);

					double actual1 = Math.min(
							Math.abs(angleDifference(c + Math.PI / 2, a1)),
							Math.abs(angleDifference(c - Math.PI / 2, a1)));
					double actual2 = Math.min(
							Math.abs(angleDifference(c + Math.PI / 2, a2)),
							Math.abs(angleDifference(c - Math.PI / 2, a2)));
					if (actual2 < actual1)
						champ = l;
				}
				return champ;
			} else {
				return null;
			}

		}

		private void bounce(Line l) {
			double N = -Math.PI / 2 + Math.atan2(l.BY - l.AY, l.BX - l.AX);
			this.transformDirection(N);
			this.createSparks(N, getColorForSwitch(l.rect.hp));

			if (l.rect.hp <= 1 && !l.rect.destroy) {
				DeadBlock db = new DeadBlock(l.rect.lines[0].AX,
						l.rect.lines[0].AY, l.rect.lines[1].AX,
						l.rect.lines[1].AY, l.rect.lines[2].AX,
						l.rect.lines[2].AY, l.rect.lines[3].AX,
						l.rect.lines[3].AY, 2 * Math.cos(N), 2 * Math.sin(N),
						12);
				dbList.add(db);
			}
			beep1();
			l.rect.hit();
		}

		public void launch() {
			if (!this.shot) {
				beep3();
				this.shot = true;
				this.xsp = ball_speed * spdf * Math.cos(Math.PI / 4);
				this.ysp = -ball_speed * spdf * Math.sin(Math.PI / 4);
			}
		}

		public void update() {
			if (!shot) {
				this.x = paddle.x + paddle.width / 2;
				this.y = paddle.y - this.radius - 1;
				return;
			}

			if (Math.abs(ysp) < 1) {
				ysp = Math.signum(ysp);
			}

			if (collisionRectRect(this.x - this.radius, this.y - this.radius,
					this.x + this.radius, this.y + this.radius, paddle.x,
					paddle.y, paddle.x + paddle.width, paddle.y + paddle.height)) {
				paddle.light = 1.0;
				double newdir = (-90 + 75
						* (ball.x - (paddle.x + paddle.width / 2))
						/ paddle.width)
						* Math.PI / 180;
				createSparks(-newdir, Color.WHITE);
				setDirection(newdir);
				if (this.y + 2 * this.radius > paddle.y) {
					this.y = paddle.y - 2 * this.radius - 1;
				}
				beep3();
				camy += force;
			}

			if (this.x + this.radius > orig_scr_wid && this.xsp > 0) {
				this.x = orig_scr_wid - this.radius;
				this.xsp *= -1;
				right_wall = 1;
				this.createSparks(Math.PI, Color.LIGHT_GRAY);
				beep2();
				camx += force;
			} else if (this.x - this.radius < 0 && this.xsp < 0) {
				this.x = this.radius;
				this.xsp *= -1;
				left_wall = 1;
				this.createSparks(0, Color.LIGHT_GRAY);
				beep2();
				camx -= force;
			} else if (this.y - this.radius < 0 && this.ysp < 0) {
				this.y = this.radius;
				this.ysp *= -1;
				top_wall = 1;
				this.createSparks(3 * Math.PI / 2, Color.LIGHT_GRAY);
				beep2();
				camy -= force;
			} else if (this.y > orig_scr_hgt && this.ysp > 0) {
				this.xsp = 0;
				this.ysp = 0;
				this.shot = false;
				score /= 2;
				beepDead();
			}

			Line tempL = null;
			int mult = (int) dist(0, 0, this.xsp, this.ysp);

			for (int i = 0; i < mult; i++) {
				tempL = checkPlace(xsp / mult, ysp / mult);
				if (tempL != null) {
					bounce(tempL);
				} else {
					x += xsp / mult;
					y += ysp / mult;
				}
			}

		}

		public void render(Graphics2D g2) {
			g2.setColor(Color.WHITE);
			g2.fillOval((int) (this.x - this.radius),
					(int) (this.y - this.radius), 2 * this.radius,
					2 * this.radius);
		}
	}

	public Game() {

		/*
		 * REMOVE CURSOR code BufferedImage cursorImg = new BufferedImage(16,
		 * 16, BufferedImage.TYPE_INT_ARGB); Cursor blankCursor =
		 * Toolkit.getDefaultToolkit().createCustomCursor( cursorImg, new
		 * Point(0, 0), "blank cursor"); this.setCursor(blankCursor);
		 */

		this.setFocusable(true);

		MouseAdapter ml = new ML();
		this.addMouseListener(ml);
		this.addMouseMotionListener(ml);
		this.addKeyListener(new KL());
		this.addComponentListener(new CL());

		blockList = new ArrayList<Block>();
		particleList = new ArrayList<Particle>();
		dbList = new ArrayList<DeadBlock>();

		Timer t = new Timer(1000, updateFPS);
		t.setInitialDelay(0);
		t.start();

		ball = new Ball(0, 0);
		paddle = new Paddle(256, 540, 64, 8);

		Path2D.Double path = new Path2D.Double();
		path.moveTo(100, 100);
		path.quadTo(400, 200, 700, 100);
		path.quadTo(550, 300, 400, 200);
		path.quadTo(250, 100, 100, 300);
		path.quadTo(400, 400, 700, 300);

		AffineTransform at = new AffineTransform();
		int cx = 40;
		int cy = 60;

		int pinx1 = 0;
		int piny1 = 0;
		int pinx2 = 0;
		int piny2 = 0;
		Block prev = null;

		FlatteningPathIterator fpi = new FlatteningPathIterator(
				path.getPathIterator(at), 3, 6);
		PathIterator pi = fpi;
		int segnumber = 0;
		while (pi.isDone() == false) {
			segnumber++;
			float[] coords = new float[6];
			pi.currentSegment(coords);
			double angle = (Math.atan2(coords[1] - cy, coords[0] - cx));
			double wid = 8;

			int tx1 = (int) (coords[0] + wid * Math.cos(angle + Math.PI / 2));
			int ty1 = (int) (coords[1] + wid * Math.sin(angle + Math.PI / 2));
			int tx2 = (int) (coords[0] + wid * Math.cos(angle - Math.PI / 2));
			int ty2 = (int) (coords[1] + wid * Math.sin(angle - Math.PI / 2));

			Block block = null;
			if (pinx1 == 0 && piny1 == 0) {
				block = new Block((int) (cx + wid
						* Math.cos(angle + Math.PI / 2)), (int) (cy + wid
						* Math.sin(angle + Math.PI / 2)), tx1, ty1, tx2, ty2,
						(int) (cx + wid * Math.cos(angle - Math.PI / 2)),
						(int) (cy + wid * Math.sin(angle - Math.PI / 2)),
						1 + segnumber / 6);
			} else {
				block = new Block(pinx1, piny1, tx1, ty1, tx2, ty2, pinx2,
						piny2, 1 + segnumber / 6);
			}

			block.setPrev(prev);
			if (block.getPrev() != null) {
				(block.getPrev()).setNext(block);
			}

			blockList.add(block);
			cx = (int) coords[0];
			cy = (int) coords[1];
			pinx1 = tx1;
			piny1 = ty1;
			pinx2 = tx2;
			piny2 = ty2;
			prev = block;
			pi.next();
		}

		new Thread() {
			public void run() {
				long last_loop = System.nanoTime();

				while (running) {

					last_loop = System.nanoTime();

					update();
					repaint();

					try {
						Thread.sleep(Math.max(
								fps_adjust
										+ (last_loop - System.nanoTime() + optimal_time)
										/ 1000000, 1));
					} catch (InterruptedException e) {
						System.out.println("interrupted");
					}
					lastUpdateTime = System.currentTimeMillis();
				}
			}
		}.start();

	}

	// remove dead entities
	private void sweep() {
		// SWEEPER
		ArrayList<Particle> copyP = new ArrayList<Particle>(particleList);
		for (Particle p : copyP) {
			if (p.destroy == true) {
				particleList.remove(p);
			}
		}
		ArrayList<Block> copyB = new ArrayList<Block>(blockList);
		for (Block b : copyB) {
			if (b.destroy == true) {
				blockList.remove(b);
			}
		}
		ArrayList<DeadBlock> copyD = new ArrayList<DeadBlock>(dbList);
		for (DeadBlock d : copyD) {
			if (d.destroy == true) {
				dbList.remove(d);
			}
		}
	}

	private void update_walls() {
		if (left_wall > 0) {
			left_wall -= 0.05;
		}
		if (top_wall > 0) {
			top_wall -= 0.05;
		}
		if (right_wall > 0) {
			right_wall -= 0.05;
		}

		if (left_wall < 0) {
			left_wall = 0;
		}
		if (top_wall < 0) {
			top_wall = 0;
		}
		if (right_wall < 0) {
			right_wall = 0;
		}

		if (scr_li > 0) {

			scr_li -= 0.15 * spdf;
		}
		if (scr_li < 0) {
			scr_li = 0;
		}

		double dis = dist(0, 0, camx, camy);
		double dir = Math.atan2(-camy, -camx);
		camx += Math.cos(dir) * dis * 0.05;
		camy += Math.sin(dir) * dis * 0.05;

	}

	private void render_walls(Graphics2D g2) {
		g2.setStroke(new BasicStroke(6 + 2 * left_wall));
		g2.setColor(blend(Color.GRAY, Color.WHITE, left_wall));
		g2.drawLine(0, 0, 0, orig_scr_hgt);

		g2.setStroke(new BasicStroke(6 + 2 * top_wall));
		g2.setColor(blend(Color.GRAY, Color.WHITE, top_wall));
		g2.drawLine(0, 0, orig_scr_wid - 1, 0);

		g2.setStroke(new BasicStroke(6 + 2 * right_wall));
		g2.setColor(blend(Color.GRAY, Color.WHITE, right_wall));
		g2.drawLine(orig_scr_wid - 1, 0, orig_scr_wid - 1, orig_scr_hgt);
	}

	public void update() {
		ball.update();
		paddle.update();

		for (Block b : blockList) {
			b.update();
		}

		for (Particle p : particleList) {
			p.update();
		}

		for (DeadBlock d : dbList) {
			d.update();
		}

		update_walls();

		sweep();

		totalFrameCount++;
	}

	private Color getColorForSwitch(int sw) {
		switch (sw) {
		case 1:
			return Color.RED;
		case 2:
			return c_orange;
		case 3:
			return Color.YELLOW;
		case 4:
			return Color.GREEN;
		case 5:
			return c_blue;
		case 6:
			return Color.MAGENTA;
		case 7:
			return c_purple;
		default:
			return Color.WHITE;
		}
	}

	public void paintComponent(Graphics g) {
		super.paintComponent(g);
		Graphics2D g2 = (Graphics2D) g;
		g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
				RenderingHints.VALUE_ANTIALIAS_ON);

		sweep();

		g2.scale(xscale, yscale);
		g2.translate(camx, camy);
		g2.setColor(Color.BLACK);
		g2.fillRect((int) (-camx), (int) (-camy), (int) (orig_scr_wid),
				(int) (orig_scr_hgt));

		this.render_walls(g2);

		for (DeadBlock d : dbList) {
			d.render(g2);
		}

		for (Block b : blockList) {
			b.render(g2);
		}

		paddle.render(g2);

		ball.render(g2);

		for (Particle p : particleList) {
			p.render(g2);
		}

		double li = Math.sin(scr_li * Math.PI / 2);
		g2.setColor(blend(
				getColorForSwitch((int) (1 + 6 * draw_score / 17000)),
				Color.WHITE, li * 0.5 + 0.25));

		double maxin = 20.0 * (double) draw_score / 17000;

		// scr_li = (score - draw_score)/200;

		if (score > draw_score) {
			draw_score += ((score - draw_score) * 0.2);
		} else if (score < Math.round(draw_score)) {
			double dir = Math.random() * Math.PI * 2;
			camx += 2 * Math.cos(dir);
			camy += 2 * Math.sin(dir);
			draw_score += ((score - draw_score) * 0.05);
		}

		g2.setFont(new Font("IMPACT", Font.BOLD, (int) (maxin + 22 + 10 * li)));
		g2.drawString("SCORE: " + String.valueOf((int) Math.round(draw_score)),
				8, (int) (28 + maxin));

		g2.setColor(Color.GRAY);
		g2.setFont(new Font("IMPACT", Font.PLAIN, 12));
		g2.drawString("FPS: " + String.valueOf(current_fps), 8,
				(int) (48 + maxin));

	}

	public static void main(String args[]) {
		JFrame window = new JFrame("Breakout");
		window.setLocationByPlatform(true);
		window.setSize(800, 600);
		window.setContentPane(new Game());
		window.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		window.setVisible(true);
	}

	public class ML extends MouseAdapter {
		public void mouseMoved(MouseEvent e) {
			pos_x = (int) (e.getX() / xscale);
			pos_y = (int) (e.getY() / yscale);
			paddle.mouse = (int) (60 / spdf);
		}

		public void mousePressed(MouseEvent e) {
			if (e.getButton() == MouseEvent.BUTTON1) {
				ball.launch();
			}
		}

		public void mouseClicked(MouseEvent e) {
			if (e.getButton() == MouseEvent.BUTTON1) {
				ball.launch();
			}
		}
	}

	public class KL extends KeyAdapter {
		public void keyPressed(KeyEvent e) {
			int key = e.getKeyCode();

			if (key == KeyEvent.VK_LEFT) {
				vk_left = true;
			} else if (key == KeyEvent.VK_RIGHT) {
				vk_right = true;
			} else if (key == KeyEvent.VK_SPACE) {
				ball.launch();
			}

		}

		public void keyReleased(KeyEvent e) {
			int key = e.getKeyCode();

			if (key == KeyEvent.VK_LEFT) {
				vk_left = false;
			} else if (key == KeyEvent.VK_RIGHT) {
				vk_right = false;
			}
		}
	}

	public class CL extends ComponentAdapter {
		public void componentResized(ComponentEvent e) {
			screen_width = e.getComponent().getWidth();
			screen_height = e.getComponent().getHeight();
			xscale = (double) e.getComponent().getWidth()
					/ (double) orig_scr_wid;
			yscale = (double) e.getComponent().getHeight()
					/ (double) orig_scr_hgt;
			e.getComponent().repaint();
		}
	}

}
