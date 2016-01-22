import java.awt.*;
import java.awt.event.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.util.*;

import javax.swing.*;
import javax.swing.Timer;

public class Game extends JPanel implements Runnable {
	private static final long serialVersionUID = -9L;
	boolean running = true;
	Thread animator;

	int totalFrameCount = 0;
	int current_fps = 0;
	int fps_adjust = 0;
	final int target_fps = 60;
	final long optimal_time = 1000000000 / target_fps;
	final int orig_scr_wid = 800;
	final int orig_scr_hgt = 600;

	private double lastUpdateTime = System.nanoTime();
	private int pos_x = 0;
	private int pos_y = 0;
	private double xscale = 1.0;
	private double yscale = 1.0;

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

	public void addNotify() {
		super.addNotify();
		animator = new Thread(this);
		animator.start();
	}

	ActionListener updateFPS = new ActionListener() {
		@Override
		public void actionPerformed(ActionEvent event) {
			current_fps = totalFrameCount;
			if (current_fps + 2 < target_fps) {
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
			this.gravity = 0;
		}

		public void update() {
			this.life--;
			if (this.life <= 0) {
				this.destroy();
			}
			this.ysp += this.gravity;
			this.x += this.xsp;
			this.y += this.ysp;
		}

		private void destroy() {
			this.destroy = true;
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
			this.life = li;
			this.destroy = false;
			this.maxlife = li;
			this.gravity = 0.25;
			this.col = c;
		}

		public void update() {
			this.life--;
			if (this.life <= 0) {
				this.destroy();
			}
			this.ysp += this.gravity;
			this.x += this.xsp;
			this.y += this.ysp;
		}

		private void destroy() {
			this.destroy = true;
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
			this.delay = delay;
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
			this.delay = delay;
			if (this.light > 1)
				this.light = 1;
			if (this.getNext() != null)
				this.getNext().lightUpNext(li / 1.5, delay + 5);
		}

		public void hit() {
			this.light = 1.0;
			this.amp = 1.0;

			if (this.prev != null)
				this.getPrev().lightUpPrev(1.0 / 1.5, 2);
			if (this.next != null)
				this.getNext().lightUpNext(1.0 / 1.5, 2);

			this.hp--;
			if (this.hp <= 0) {
				this.destroy();
			}
		}

		public void update() {

			if (delay > 0) {
				delay--;
			} else {
				if (light > 0) {

					light -= 0.15;
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
	}

	private class Paddle extends Rect {
		private int hsp;
		private double light;

		public Paddle(int x, int y, int width, int height) {
			super(x, y, width, height);
			this.hsp = 32;
			this.light = 0;
		}

		private void moveTo(int dx) {
			if (Math.abs(dx - this.x) > this.hsp) {
				this.x += this.hsp * Math.signum((double) (dx - this.x));
			} else {
				this.x = dx;
			}
		}

		public void update() {
			if (light > 0) {
				light -= 0.05;
			}

			if (light < 0) {
				light = 0;
			}
		}
	}

	private class Ball {
		private double x;
		private double y;
		private double xsp;
		private double ysp;
		private int radius;

		public Ball(double xx, double yy) {
			this.x = xx;
			this.y = yy;
			this.radius = 4;

			this.xsp = 3;
			this.ysp = -4;
		}

		private void transformDirection(double N) {

			double c_speed = dist(0, 0, this.xsp, this.ysp);
			double c_angle = Math.atan2(this.ysp, this.xsp);
			double e_angle = 2 * N - c_angle - Math.PI;
			this.xsp = c_speed * Math.cos(e_angle);
			this.ysp = c_speed * Math.sin(e_angle);

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
							Math.abs(angleDifference(c - Math.PI / 2, a1))
							);
					double actual2 = Math.min(
							Math.abs(angleDifference(c + Math.PI / 2, a2)),
							Math.abs(angleDifference(c - Math.PI / 2, a2))
							);
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
			l.rect.hit();
		}

		private void update() {
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
			}

			if (this.x + this.radius > orig_scr_wid && this.xsp > 0) {
				this.x = orig_scr_wid - this.radius;
				this.xsp *= -1;
				right_wall = 1;
				this.createSparks(Math.PI, Color.LIGHT_GRAY);
			} else if (this.x - this.radius < 0 && this.xsp < 0) {
				this.x = this.radius;
				this.xsp *= -1;
				left_wall = 1;
				this.createSparks(0, Color.LIGHT_GRAY);
			} else if (this.y - this.radius < 0 && this.ysp < 0) {
				this.y = this.radius;
				this.ysp *= -1;
				top_wall = 1;
				this.createSparks(3 * Math.PI / 2, Color.LIGHT_GRAY);
			} else if (this.y + this.radius > orig_scr_hgt && this.ysp > 0) {
				// game over
				this.ysp *= -1;
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
	}

	public Game() {
		BufferedImage cursorImg = new BufferedImage(16, 16,
				BufferedImage.TYPE_INT_ARGB);
		Cursor blankCursor = Toolkit.getDefaultToolkit().createCustomCursor(
				cursorImg, new Point(0, 0), "blank cursor");
		this.setCursor(blankCursor);

		this.setFocusable(true);

		this.addMouseMotionListener(new ML());
		this.addKeyListener(new KL());
		this.addComponentListener(new CL());
		blockList = new ArrayList<Block>();
		particleList = new ArrayList<Particle>();
		dbList = new ArrayList<DeadBlock>();

		Timer t = new Timer(1000, updateFPS);
		t.setInitialDelay(0);
		t.start();

		ball = new Ball(128, 500);
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
		PathIterator pi = fpi;// path.getPathIterator(at);
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

	}

	public void run() {

		long last_loop = System.nanoTime();

		while (running) {

			long now = System.nanoTime();
			last_loop = now;

			update();
			repaint();

			try {
				Thread.sleep(fps_adjust
						+ (last_loop - System.nanoTime() + optimal_time)
						/ 1000000);
			} catch (InterruptedException e) {
				System.out.println("interrupted");
			}
			lastUpdateTime = System.currentTimeMillis();
		}
	}

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
	}

	public void update() {
		paddle.moveTo(pos_x - paddle.width / 2);
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
			return Color.ORANGE;
		case 3:
			return Color.YELLOW;
		case 4:
			return Color.GREEN;
		case 5:
			return Color.BLUE;
		case 6:
			return Color.MAGENTA;
		case 7:
			return Color.lightGray;
		default:
			return Color.WHITE;
		}
	}

	public void paint(Graphics g) {
		super.paint(g);
		Graphics2D g2 = (Graphics2D) g;
		g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
				RenderingHints.VALUE_ANTIALIAS_ON);
		Font font = new Font("Serif", Font.PLAIN, 14);
		g2.setFont(font);
		sweep();

		g2.scale(xscale, yscale);
		g2.setColor(Color.BLACK);
		g2.fillRect(0, 0, orig_scr_wid, orig_scr_hgt);

		g2.setStroke(new BasicStroke(3 + 2 * left_wall));
		g2.setColor(blend(Color.GRAY, Color.WHITE, left_wall));
		g2.drawLine(0, 0, 0, orig_scr_hgt);

		g2.setStroke(new BasicStroke(3 + 2 * top_wall));
		g2.setColor(blend(Color.GRAY, Color.WHITE, top_wall));
		g2.drawLine(0, 0, orig_scr_wid - 1, 0);

		g2.setStroke(new BasicStroke(3 + 2 * right_wall));
		g2.setColor(blend(Color.GRAY, Color.WHITE, right_wall));
		g2.drawLine(orig_scr_wid - 1, 0, orig_scr_wid - 1, orig_scr_hgt);

		for (DeadBlock d : dbList) {
			g2.setColor(new Color(128, 0, 0, Math.min(
					(int) (2550.0 * ((float) (d.life) / (float) (d.maxlife))),
					255)));

			int[] x_points = { (int) (d.X1 + d.x), (int) (d.X2 + d.x),
					(int) (d.X3 + d.x), (int) (d.X4 + d.x) };
			int[] y_points = { (int) (d.Y1 + d.y), (int) (d.Y2 + d.y),
					(int) (d.Y3 + d.y), (int) (d.Y4 + d.y) };
			g2.fillPolygon(x_points, y_points, 4);
			g2.setColor(Color.BLACK);
			g2.setStroke(new BasicStroke( (d.maxlife - d.life) ));
			g2.drawPolygon(x_points, y_points, 4);
		}

		
		Color c = Color.WHITE;
		for (Block b : blockList) {
			c = getColorForSwitch(b.hp);

			g2.setColor(blend(c, Color.WHITE,
					b.amp * Math.sin(b.light * Math.PI)));
			int[] x_points = { b.lines[0].AX, b.lines[1].AX, b.lines[2].AX,
					b.lines[3].AX };
			int[] y_points = { b.lines[0].AY, b.lines[1].AY, b.lines[2].AY,
					b.lines[3].AY };
			g2.fillPolygon(x_points, y_points, 4);
			g2.setColor(Color.BLACK);
			for (Line l : b.lines) {
				g2.setStroke(new BasicStroke((float) (6 /*- (b.hp/3)*/- 4
						* b.amp * Math.sin(b.light * Math.PI))));
				g2.drawLine(l.AX, l.AY, l.BX, l.BY);
			}
		}
		
		g2.setColor(blend(Color.GRAY, Color.WHITE, paddle.light));
		g2.fillRect((int) (paddle.x - 8 * paddle.light),
				(int) (paddle.y + 2 * paddle.light),
				(int) (paddle.width + 16 * paddle.light),
				(int) (paddle.height - 2 * paddle.light));

		g2.setColor(Color.WHITE);
		g2.fillOval((int) (ball.x - ball.radius), (int) (ball.y - ball.radius),
				2 * ball.radius, 2 * ball.radius);

		for (Particle p : particleList) {
			double sp = dist(0, 0, p.xsp, p.ysp);
			if (sp > 4) {
				g2.setStroke(new BasicStroke(1));
			} else if (sp > 2) {
				g2.setStroke(new BasicStroke(2));
			} else {
				g2.setStroke(new BasicStroke(3));
			}

			g2.setColor(new Color(p.col.getRed(), p.col.getGreen(), p.col
					.getBlue(), Math.min(
					(int) (2550.0 * ((float) (p.life) / (float) (p.maxlife))),
					255)));
			g2.drawLine((int) p.x, (int) p.y, (int) (p.x - p.xsp),
					(int) (p.y - p.ysp));
		}

		g2.setColor(Color.WHITE);
		g2.drawString("FPS: " + String.valueOf(current_fps), 8, 18);

	}

	public static void main(String args[]) {
		JFrame window = new JFrame("Breakout");
		window.setLocationByPlatform(true);
		window.setSize(800, 600);
		window.setContentPane(new Game());
		window.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		window.setVisible(true);
	}

	public class ML extends MouseMotionAdapter {
		public void mouseMoved(MouseEvent e) {
			pos_x = (int) (e.getX() / xscale);
			pos_y = (int) (e.getY() / yscale);
		}
	}

	public class KL extends KeyAdapter {
		public void keyPressed(KeyEvent e) {
			int key = e.getKeyCode();

			if (key == KeyEvent.VK_LEFT) {
				paddle.moveTo(paddle.x - paddle.hsp);
			} else if (key == KeyEvent.VK_RIGHT) {
				paddle.moveTo(paddle.x + paddle.hsp);
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
